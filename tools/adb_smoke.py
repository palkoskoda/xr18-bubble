"""Exercise the installed debug APK on a disposable emulator, with a local fake XR18.

Never runs against a physical device. Does not use or contact a real mixer.
"""
import argparse
import json
from pathlib import Path
import re
import socket
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from fake_mixer import osc_encode

ROOT = Path(__file__).resolve().parents[1]
PKG = "sk.palko.xr18bubble"
parser = argparse.ArgumentParser()
parser.add_argument("--adb", default=r"D:\AndroidLab\sdk\platform-tools\adb.exe")
parser.add_argument("--serial", default="emulator-5584")
args = parser.parse_args()
if not args.serial.startswith("emulator-"):
    raise SystemExit("This test only permits an emulator serial")

def adb(*command, data=None):
    return subprocess.run([args.adb, "-s", args.serial, *command], input=data,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=35, check=True).stdout

def ui():
    output=adb("shell", "uiautomator dump /data/local/tmp/xr18-smoke.xml")
    if b"dumped to" not in output: return ET.Element("hierarchy")
    return ET.fromstring(adb("shell", "cat /data/local/tmp/xr18-smoke.xml"))

def bounds(node):
    return tuple(map(int, re.findall(r"\d+", node.attrib["bounds"])))

def click(node):
    x1,y1,x2,y2=bounds(node)
    adb("shell", f"input tap {(x1+x2)//2} {(y1+y2)//2}")

def screenshot(name):
    (ROOT/"build"/name).write_bytes(adb("exec-out", "screencap", "-p"))

def wait_for(test, seconds=60):
    deadline=time.monotonic()+seconds
    while time.monotonic()<deadline:
        tree=ui()
        if test(tree): return tree
        time.sleep(.5)
    raise AssertionError("Expected UI state was not reached")

def texts(tree): return [n.get("text","") for n in tree.iter("node")]

def mute_buttons(tree):
    return [n for n in tree.iter("node") if n.get("class")=="android.widget.Button" and n.get("text") in ("MUTE","MUTED","—")]

log=ROOT/"build"/"smoke-writes.jsonl"
log.write_text("",encoding="utf-8")
def start_fake():
    return subprocess.Popen([sys.executable,"-u",str(ROOT/"tools"/"fake_mixer.py"),"--host","127.0.0.1","--log",str(log)],cwd=ROOT)

def events():
    return [json.loads(s) for s in log.read_text(encoding="utf-8").splitlines() if s]

fake=start_fake()
try:
    time.sleep(.3)
    adb("shell", f"am force-stop {PKG}")
    profile=b'<?xml version="1.0" encoding="utf-8"?><map><string name="host">10.0.2.2</string><string name="security">OPEN</string></map>'
    adb("shell",f"run-as {PKG} sh -c 'mkdir -p shared_prefs; cat > shared_prefs/profile.xml'",data=profile)
    adb("shell",f"appops set {PKG} SYSTEM_ALERT_WINDOW allow")
    adb("shell",f"pm grant {PKG} android.permission.POST_NOTIFICATIONS")
    adb("shell",f"am start -n {PKG}/.MainActivity")
    tree=wait_for(lambda t:"Wi-Fi + mix" in texts(t))
    click(next(n for n in tree.iter("node") if n.get("text")=="Wi-Fi + mix"))
    tree=wait_for(lambda t:any("XR18 test" in n.get("text","") for n in t.iter("node")))
    assert not events(), "Connecting must not send SET"
    screenshot("bubble.png")
    # Default bubble position on this dedicated 432x960 mdpi test emulator, verified in bubble.png.
    adb("shell","input tap 399 233")
    tree=wait_for(lambda t:"MUTE" in texts(t) and "MAIN LR" in texts(t))
    screenshot("panel-connected.png")
    mute=sorted([n for n in tree.iter("node") if n.get("text")=="MUTE"],key=lambda n:bounds(n)[0])[0]
    assert mute.get("enabled")=="true"
    click(mute)
    tree=wait_for(lambda t:"MUTED" in texts(t))
    assert any(e["path"]=="/lr/mix/on" and e["value"]==0 for e in events())
    click(next(n for n in tree.iter("node") if n.get("text")=="MUTED"))
    tree=wait_for(lambda t:"MUTED" not in texts(t))
    assert any(e["path"]=="/lr/mix/on" and e["value"]==1 for e in events())
    with socket.socket(socket.AF_INET,socket.SOCK_DGRAM) as sock:
        sock.sendto(osc_encode("/lr/mix/fader",.5),("127.0.0.1",10024))
    tree=wait_for(lambda t:"-10.0" in texts(t))
    fader=sorted([n for n in tree.iter("node") if "Hlasitosť" in n.get("content-desc","")],key=lambda n:bounds(n)[0])[0]
    x1,y1,x2,y2=bounds(fader);x=(x1+x2)//2;y=(y1+y2)//2
    adb("shell",f"input swipe {x} {y} {x} {y-20} 350")
    time.sleep(.7)
    written=[e["value"] for e in events() if e["path"]=="/lr/mix/fader" and e["value"]!=.5]
    assert written and .5<written[-1]<.8, written
    screenshot("panel-fader.png")
    fake.terminate();fake.wait(timeout=10)
    tree=wait_for(lambda t:len(mute_buttons(t))==3 and all(n.get("enabled")=="false" for n in mute_buttons(t)))
    screenshot("panel-disconnected.png")
    count=len(events());time.sleep(1)
    fake=start_fake()
    tree=wait_for(lambda t:"MUTE" in texts(t) and "-4.0" in texts(t),seconds=25)
    assert len(events())==count,"No SET may replay after reconnect"
    screenshot("panel-reconnected.png")
    print("ADB smoke passed: handshake, no writes on connect, mute/unmute, external sync, relative fader, stale disable, reconnect without replay")
finally:
    fake.terminate()
    fake.wait(timeout=10)
