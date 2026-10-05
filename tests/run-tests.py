from pathlib import Path
import subprocess, argparse, os
root=Path(__file__).resolve().parent.parent
parser=argparse.ArgumentParser(description='Windows desktop protocol and command tests')
parser.add_argument('--java-home',default=os.environ.get('JAVA_HOME',''))
args=parser.parse_args()
if os.name!='nt': parser.error('These command-execution fixtures require Windows PowerShell.')
if not args.java_home: parser.error('Set JAVA_HOME or pass --java-home.')
java=Path(args.java_home)/'bin/java.exe'
source=root/'src'
classes=root/'build/test-classes'
classes.mkdir(parents=True,exist_ok=True)
subprocess.run([str(java.parent/'javac.exe'),'--release','8','-encoding','UTF-8','-d',str(classes),str(source/'CommandRunner.java'),str(source/'ConsoleServer.java'),str(root/'tests/TestConsole.java')],check=True)
subprocess.run([str(java),'-Dsun.net.http.allowRestrictedHeaders=true','-cp',str(classes),'com.e02.rootconsole.TestConsole','C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe',str(source/'assets/console.html')],check=True)
subprocess.run([str(java.parent/'javac.exe'),'--release','8','-encoding','UTF-8','-cp',str(classes),'-d',str(classes),str(source/'BridgeRunner.java'),str(root/'tests/TestBridge.java')],check=True)
subprocess.run([str(java),'-cp',str(classes),'com.e02.rootconsole.TestBridge'],check=True)
subprocess.run([str(java.parent/'javac.exe'),'--release','8','-encoding','UTF-8','-d',str(classes),str(source/'BridgeLifetime.java'),str(root/'tests/TestLifetime.java')],check=True)
subprocess.run([str(java),'-cp',str(classes),'com.e02.rootconsole.TestLifetime'],check=True)
subprocess.run([str(java.parent/'javac.exe'),'--release','8','-encoding','UTF-8','-d',str(classes),str(source/'AdbControl.java'),str(root/'tests/TestAdbControl.java')],check=True)
subprocess.run([str(java),'-cp',str(classes),'com.e02.rootconsole.TestAdbControl'],check=True)
