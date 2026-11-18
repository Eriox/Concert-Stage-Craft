"""Opt-in developer test. Requires Java 17, an ordinary Gradle environment, and a display (or Xvfb).
Run the Forge GameTest server once to create its disposable world first.
"""
from pathlib import Path
import os,sys,subprocess,shutil,wave,struct,math,hashlib,tempfile,socket,time
root=Path(__file__).resolve().parents[2];forge=root/'forge';run=forge/'run';world=run/'saves/CSC_Audio_Review'
if world.exists():shutil.rmtree(world)
if not world.exists():
    original=forge/'gametest-run/world'
    if not original.exists():raise SystemExit('First run gradlew runGameTestServer from forge/.')
    shutil.copytree(original,world)
run.mkdir(exist_ok=True)
options=run/'options.txt'
# This is the development run directory, not the user's normal Minecraft installation.
options.write_text('version:3465\nguiScale:2\nrenderDistance:2\nsimulationDistance:3\nsoundCategory_music:0.0\nsoundCategory_records:1.0\nsoundCategory_master:1.0\ncloudStatus:0\nmaxFps:30\ntutorialStep:none\n')
cache=run/'concertstage/audio_cache';cache.mkdir(parents=True,exist_ok=True)
mp3sha=None;mp3size=0
with tempfile.TemporaryDirectory() as temp:
    wav=Path(temp)/'test.wav';rate=44100
    with wave.open(str(wav),'wb') as f:
        f.setnchannels(2);f.setsampwidth(2);f.setframerate(rate)
        block=b''.join(struct.pack('<hh',int(32767*(.08*math.sin(2*math.pi*440*i/rate)+.05*math.sin(2*math.pi*65*i/rate))),int(32767*(.08*math.sin(2*math.pi*660*i/rate)+.05*math.sin(2*math.pi*65*i/rate)))) for i in range(rate))
        for _ in range(180):f.writeframesraw(block)
    sha=hashlib.sha1(wav.read_bytes()).hexdigest();shutil.copy2(wav,cache/(sha+'.wav'));size=wav.stat().st_size
    if os.environ.get('CSC_AUDIO_LONG_TEST')=='1':
        mp3=Path(temp)/'test.mp3'
        subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-i',str(wav),'-codec:a','libmp3lame','-b:a','192k',str(mp3)],check=True)
        mp3sha=hashlib.sha1(mp3.read_bytes()).hexdigest();mp3size=mp3.stat().st_size;shutil.copy2(mp3,cache/(mp3sha+'.mp3'))
env=os.environ.copy();env.update(ALSOFT_DRIVERS='null',CSC_SMOKE_SHA=sha,CSC_SMOKE_SIZE=str(size))
if mp3sha:env.update(CSC_SMOKE_MP3_SHA=mp3sha,CSC_SMOKE_MP3_SIZE=str(mp3size))
x=None
if sys.platform.startswith('linux') and not env.get('DISPLAY'):
    executable=shutil.which('Xvfb')
    if not executable:raise SystemExit('A graphical desktop or Xvfb is required for the client test.')
    env['DISPLAY']='127.0.0.1:97';runtime=run/'xdg-runtime';runtime.mkdir(exist_ok=True);runtime.chmod(0o700);env['XDG_RUNTIME_DIR']=str(runtime);x=subprocess.Popen([executable,':97','-screen','0','1280x900x24','-ac','-noreset','-nolock','-nolisten','unix','-nolisten','local','-listen','tcp'])
    for attempt in range(50):
        if x.poll() is not None:raise SystemExit('Xvfb failed to start')
        try:
            with socket.create_connection(('127.0.0.1',6097),timeout=.2):break
        except OSError:time.sleep(.1)
    else:raise SystemExit('Xvfb display did not become ready')
try:
    wrapper=forge/('gradlew.bat' if os.name=='nt' else 'gradlew')
    if env.get('CSC_BOOTSTRAP'):
        command=[sys.executable,env['CSC_BOOTSTRAP'],'--gradle-user-home',env['GRADLE_USER_HOME'],'--project-cache-dir',env['CSC_PROJECT_CACHE'],'-PcscBuildDirectory='+env['CSC_BUILD_DIRECTORY'],'-PcscSmoke','runClient']
    else:command=[str(wrapper),'-PcscSmoke','runClient']
    result=subprocess.run(command,cwd=forge,env=env)
finally:
    if x is not None:x.terminate();x.wait()
raise SystemExit(result.returncode)
