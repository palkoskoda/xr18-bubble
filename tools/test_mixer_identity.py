"""Compile and run MixerIdentityTest without an Android device or mixer."""

from pathlib import Path
import os
import shutil
import subprocess
import sys
import tempfile


ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app" / "src" / "main" / "java" / "sk" / "palko" / "xr18bubble"
TEST = ROOT / "tests" / "MixerIdentityTest.java"


def find_android_jar() -> Path:
    explicit = os.environ.get("ANDROID_JAR")
    if explicit:
        return Path(explicit)
    sdk = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
    if sdk:
        candidates = sorted(Path(sdk).glob("platforms/android-*/android.jar"), reverse=True)
        if candidates:
            return candidates[0]
    raise RuntimeError("Set ANDROID_JAR to an Android API android.jar (android-36 recommended)")


def run(*args: str) -> None:
    subprocess.run(args, cwd=ROOT, check=True)


def main() -> int:
    android_jar = find_android_jar()
    if not android_jar.is_file():
        raise RuntimeError(f"Android API jar does not exist: {android_jar}")
    javac = shutil.which("javac")
    java = shutil.which("java")
    if not javac or not java:
        raise RuntimeError("javac and java are required")

    with tempfile.TemporaryDirectory(prefix="mixer-identity-") as tmp:
        out = Path(tmp) / "classes"
        stub = Path(tmp) / "sk" / "palko" / "xr18bubble" / "L.java"
        stub.parent.mkdir(parents=True)
        stub.write_text(
            "package sk.palko.xr18bubble;\n"
            "import android.content.Context;\n"
            "final class L { static String s(Context c, String k, Object... a) { return k; } }\n",
            encoding="utf-8",
        )
        out.mkdir()
        sources = [
            JAVA / "Osc.java",
            JAVA / "XAirProtocol.java",
            JAVA / "MeterProtocol.java",
            JAVA / "MixerClient.java",
            stub,
            TEST,
        ]
        run(javac, "-cp", str(android_jar), "-d", str(out), *(str(path) for path in sources))
        run(java, "-cp", f"{out}{os.pathsep}{android_jar}", "MixerIdentityTest")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (RuntimeError, subprocess.CalledProcessError) as error:
        print(f"test_mixer_identity: {error}", file=sys.stderr)
        raise SystemExit(2)
