param(
    [ValidateSet('debug', 'release-apk', 'release')]
    [string]$Mode = 'debug',
    [string]$SdkRoot = '',
    [string]$BuildToolsVersion = '35.0.0',
    [int]$TargetApi = 36,
    [string]$BundletoolJar = 'build\tools\bundletool-all-1.18.3.jar',
    [string]$BundleConfig = 'tools\bundle-config.json'
)

$ErrorActionPreference = 'Stop'
$previousLocation = Get-Location
try {
    Set-Location -LiteralPath $PSScriptRoot
    if ([string]::IsNullOrWhiteSpace($SdkRoot)) { $SdkRoot = $env:ANDROID_HOME }
    if ([string]::IsNullOrWhiteSpace($SdkRoot)) { $SdkRoot = $env:ANDROID_SDK_ROOT }
    if ([string]::IsNullOrWhiteSpace($SdkRoot)) {
        throw 'Android SDK not configured. Pass -SdkRoot "C:\Android\sdk" or set ANDROID_HOME (ANDROID_SDK_ROOT is also supported).'
    }
    # Keep generated paths relative to the project. This avoids tool temp failures
    # in a workspace whose absolute path contains non-ASCII characters.
    $buildRoot = Join-Path $PSScriptRoot 'build\apk'
    $distRoot = Join-Path $PSScriptRoot 'dist'
    $classesRoot = Join-Path $buildRoot 'classes'
    $dexRoot = Join-Path $buildRoot 'dex'
    $generatedRoot = Join-Path $buildRoot 'generated'
    $tempRoot = Join-Path $buildRoot 'temp'
    $protoRoot = Join-Path $buildRoot 'proto'
    $buildWorkspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'build'))
    function Remove-GeneratedPath([string]$path) {
        $resolved = [IO.Path]::GetFullPath($path)
        $prefix = $buildWorkspace.TrimEnd('\','/') + [IO.Path]::DirectorySeparatorChar
        if (!$resolved.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) {
            throw "Refusing to remove path outside build workspace: $resolved"
        }
        if (Test-Path -LiteralPath $resolved) { Remove-Item -LiteralPath $resolved -Recurse -Force }
    }
    foreach ($stale in @($classesRoot,$dexRoot,$generatedRoot,$tempRoot,$protoRoot)) {
        Remove-GeneratedPath $stale
    }
    New-Item -ItemType Directory -Force -Path $buildRoot,$classesRoot,$dexRoot,$generatedRoot,$tempRoot,$protoRoot,$distRoot | Out-Null
    $javaTemp = (Resolve-Path -LiteralPath $tempRoot).Path

    $androidJar = Join-Path $SdkRoot ("platforms\android-{0}\android.jar" -f $TargetApi)
    $buildTools = Join-Path $SdkRoot ("build-tools\{0}" -f $BuildToolsVersion)
    $aapt2 = Join-Path $buildTools 'aapt2.exe'
    $d8 = Join-Path $buildTools 'd8.bat'
    $zipalign = Join-Path $buildTools 'zipalign.exe'
    $apksigner = Join-Path $buildTools 'apksigner.bat'
    foreach ($required in @($androidJar,$aapt2,$d8,$zipalign,$apksigner)) {
        if (!(Test-Path -LiteralPath $required)) { throw "Missing Android build tool: $required" }
    }
    function Check-Exit([string]$name) { if ($LASTEXITCODE -ne 0) { throw "$name failed: $LASTEXITCODE" } }

    # aapt2 on Windows is sensitive to the accented absolute workspace path.
    $res = 'app\src\main\res'
    $manifestSource = 'app\src\main\AndroidManifest.xml'
    $manifest = Join-Path $buildRoot 'AndroidManifest.xml'
    Copy-Item -LiteralPath $manifestSource -Destination $manifest -Force
    $manifestText = [System.IO.File]::ReadAllText($manifest)
    if ($Mode -eq 'debug') {
        if ($manifestText -match 'android:debuggable="(true|false)"') {
            $manifestText = [regex]::Replace($manifestText, 'android:debuggable="(true|false)"', 'android:debuggable="true"', 1)
        } else {
            $manifestText = $manifestText -replace '<application ', '<application android:debuggable="true" ', 1
        }
        [System.IO.File]::WriteAllText($manifest, $manifestText, [System.Text.UTF8Encoding]::new($false))
    } elseif ($manifestText -match 'android:debuggable="true"') {
        throw 'Release manifest is debuggable=true; refusing to produce a release artifact.'
    }

    & $aapt2 compile --dir $res -o (Join-Path $buildRoot 'resources.zip')
    Check-Exit 'aapt2 compile'
    & $aapt2 link -o (Join-Path $buildRoot 'base.apk') -I $androidJar --manifest $manifest --java $generatedRoot (Join-Path $buildRoot 'resources.zip')
    Check-Exit 'aapt2 link'
    $sources = @(Get-ChildItem (Join-Path $PSScriptRoot 'app\src\main\java'),$generatedRoot -Filter *.java -Recurse | ForEach-Object FullName)
    & javac -encoding UTF-8 --release 8 -classpath $androidJar -d $classesRoot $sources
    Check-Exit 'javac'
    & jar "-J-Djava.io.tmpdir=$javaTemp" --create --file (Join-Path $buildRoot 'classes.jar') -C $classesRoot .
    Check-Exit 'jar'
    & $d8 --lib $androidJar --min-api 29 --output $dexRoot (Join-Path $buildRoot 'classes.jar')
    Check-Exit 'd8'

    if ($Mode -ne 'release') {
        $aligned = Join-Path $buildRoot "$Mode-aligned.apk"
        $unsigned = Join-Path $buildRoot "$Mode-unsigned.apk"
        Copy-Item -LiteralPath (Join-Path $buildRoot 'base.apk') -Destination $unsigned -Force
        & jar "-J-Djava.io.tmpdir=$javaTemp" --update --file $unsigned -C $dexRoot classes.dex
        Check-Exit 'add APK dex'
        & $zipalign -f 4 $unsigned $aligned
        Check-Exit 'APK zipalign'
        if ($Mode -eq 'release-apk') {
            $apkFile = Join-Path $distRoot 'xr18-bubble-release-unsigned.apk'
            Copy-Item -LiteralPath $aligned -Destination $apkFile -Force
            & $zipalign -c 4 $apkFile
            Check-Exit 'release APK alignment verification'
            Write-Output "Unsigned release APK: $((Resolve-Path -LiteralPath $apkFile).Path)"
            Write-Output 'Sign this APK with your publisher-controlled key before distribution. No production signing key was generated.'
        } else {
            $keyFile = Join-Path $PSScriptRoot 'build\debug.keystore'
            if (!(Test-Path -LiteralPath $keyFile)) {
                & keytool -genkeypair -keystore $keyFile -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=XR18 Bubble Development'
                Check-Exit 'debug keystore generation'
            }
            $apkFile = Join-Path $distRoot 'xr18-bubble-debug.apk'
            & $apksigner sign --ks $keyFile --ks-pass pass:android --out $apkFile $aligned
            Check-Exit 'debug signing'
            & $apksigner verify $apkFile
            Check-Exit 'debug verification'
            Write-Output "Debug APK: $((Resolve-Path -LiteralPath $apkFile).Path)"
        }
    } else {
        $moduleZip = Join-Path $protoRoot 'base.zip'
        & $aapt2 link --proto-format -o $moduleZip -I $androidJar --manifest $manifest (Join-Path $buildRoot 'resources.zip')
        Check-Exit 'aapt2 proto link'
        $moduleStage = Join-Path $protoRoot 'stage'
        if (Test-Path -LiteralPath $moduleStage) { Remove-GeneratedPath $moduleStage }
        Expand-Archive -LiteralPath $moduleZip -DestinationPath $moduleStage -Force
        New-Item -ItemType Directory -Force -Path (Join-Path $moduleStage 'manifest'),(Join-Path $moduleStage 'dex') | Out-Null
        Move-Item -LiteralPath (Join-Path $moduleStage 'AndroidManifest.xml') -Destination (Join-Path $moduleStage 'manifest\AndroidManifest.xml') -Force
        Copy-Item -LiteralPath (Join-Path $dexRoot 'classes.dex') -Destination (Join-Path $moduleStage 'dex\classes.dex') -Force
        Remove-Item -LiteralPath $moduleZip -Force
        & jar "-J-Djava.io.tmpdir=$javaTemp" --create --no-manifest --file $moduleZip -C $moduleStage .
        Check-Exit 'assemble release module'
        $aab = Join-Path $distRoot 'xr18-bubble-release-unsigned.aab'
        if (!(Test-Path -LiteralPath $BundletoolJar)) {
            throw "Pinned Google bundletool 1.18.3 is missing: $BundletoolJar"
        }
        if (!(Test-Path -LiteralPath $BundleConfig)) {
            throw "Bundletool config is missing: $BundleConfig"
        }
        if (Test-Path -LiteralPath $aab) { Remove-Item -LiteralPath $aab -Force }
        & java "-Djava.io.tmpdir=$javaTemp" -jar $BundletoolJar build-bundle --modules=$moduleZip --config=$BundleConfig --output=$aab
        Check-Exit 'bundletool build-bundle'
        & java "-Djava.io.tmpdir=$javaTemp" -jar $BundletoolJar validate --bundle=$aab
        Check-Exit 'bundletool validate'
        Write-Output "Unsigned release AAB: $((Resolve-Path -LiteralPath $aab).Path)"
        Write-Output 'No production signing key was provided; signing was intentionally skipped.'
    }
} finally {
    Set-Location -LiteralPath $previousLocation.Path
}
