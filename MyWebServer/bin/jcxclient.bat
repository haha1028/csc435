rem java compile BCClient.java with xml libraries.
rem change this path to point to your own .jar file locations:
javac -cp "xstream-1.2.1.jar;xpp3_min-1.1.3.4.O.jar" *.java
java -cp ".;xstream-1.2.1.jar;xpp3_min-1.1.3.4.O.jar" BCClient 