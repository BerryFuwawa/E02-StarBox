from pathlib import Path
import argparse,subprocess
p=argparse.ArgumentParser();p.add_argument('--java-home',required=True);args=p.parse_args()
root=Path(__file__).resolve().parent.parent
classes=root/'build/wake-observer-test-classes';classes.mkdir(parents=True,exist_ok=True)
j=Path(args.java_home)/'bin'
sources=['RootWakeObserver','RootDisplayObserver','ReadOnlyServiceLookup','DisplayWakeGate','BoundedRead','WakeRestoreController','AuxiliaryRepair','RecoveryServiceState','CommandRunner']
def run(command):subprocess.run(['rtk','proxy',*map(str,command)],check=True,timeout=45)
run([j/'javac.exe','--release','8','-encoding','UTF-8','-d',classes,*[root/('src/'+x+'.java') for x in sources],*sorted((root/'tests/wake-observer-fixtures').rglob('*.java'))])
for test in ['TestDisplayAdapter','TestWakeObserverAdapter','TestWakeObserverRebind']:
    run([j/'java.exe','-cp',classes,'com.e02.rootconsole.'+test])
