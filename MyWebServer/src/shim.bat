@echo on
rem This is shim.bat
rem Change this to your development directory:
cd D:\dropbox\Dropbox\depaul class\CSC435\assignment\MyWebServer\src
echo "We are now in a shim called from the Web Browser"
echo Arg one is: %1
rem Change this to point to your Handler directory:
D:
cd D:\dropbox\Dropbox\depaul class\CSC435\assignment\MyWebServer\src
pause
rem have to set classpath in batch, passing as arg does not work.
rem Change this to point to your own Xstream library files:
set classpath=%classpath%;D:\dropbox\Dropbox\depaul class\CSC435\assignment\MyWebServer\src\;
echo %classpath%
rem pass the name of the first argument to java:
javac -cp ".;xstream-1.2.1.jar;xpp3_min-1.1.3.4.O.jar" *.java
java -cp ".;xstream-1.2.1.jar;xpp3_min-1.1.3.4.O.jar" -Dfirstarg=%1 BCHandler
pause
