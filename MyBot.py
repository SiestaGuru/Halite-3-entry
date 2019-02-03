#wrapper for the java code so flags can be added
import subprocess

r = subprocess.Popen(['java',
'-XX:MaxTenuringThreshold=15',
'-mx820m','-ms820m','-XX:+UseG1GC', '-XX:MaxGCPauseMillis=50','-XX:G1ReservePercent=20','-server', '-XX:InitiatingHeapOccupancyPercent=0', 
'-cp' ,'build', 'MyBot'])