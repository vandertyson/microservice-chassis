import sys
import subprocess
from collections import OrderedDict

def line_contain(list_pid, line):
    for pid in list_pid:
        if pid in line:
            return True
    return False

def check_log(list_pid):
    path = "result.txt"
    stat_dict = dict()

    summation = -1
    stat_dict[summation] = OrderedDict()
    stat_dict[summation]['name'] = "SUMMATION"
    stat_dict[summation]['count'] = 0
    stat_dict[summation]['cpu-curr'] = 0.0
    stat_dict[summation]['min'] = 100000
    stat_dict[summation]['max'] = -1
    stat_dict[summation]['total'] = 0.0
    stat_dict[summation]['avg'] = 0.0

    min = 100000
    max = -1  
    total = 0.0
    count = 0
    cpu = 0.0

    with open(path) as file:
        for line in file:
            if (line_contain(list_pid, line)):
                pid = int(line.split()[0].strip())
                if pid not in stat_dict:
                    stat_dict[pid] = OrderedDict()
                    stat_dict[pid]['name'] = pid
                    stat_dict[pid]['count'] = 0
                    stat_dict[pid]['cpu-curr'] = 0.0
                    stat_dict[pid]['min'] = 100000
                    stat_dict[pid]['max'] = -1
                    stat_dict[pid]['total'] = 0.0
                    stat_dict[pid]['avg'] = 0.0

                if pid in stat_dict:
                    cpu = float(line.split()[8].strip())
                    stat_dict[pid]['cpu-curr'] = cpu
                    print("pid: ", pid, "-- cpu: ", cpu)
                    
                    stat_dict[pid]['count'] += 1

                    if cpu < stat_dict[pid]['min']:
                        stat_dict[pid]['min'] = cpu
                    if cpu > stat_dict[pid]['max']:
                        stat_dict[pid]['max'] = cpu

                    stat_dict[pid]['total'] += cpu
                    stat_dict[pid]['avg'] = stat_dict[pid]['total'] / stat_dict[pid]['count']
                   
            if "top -" in line:
                stat_dict[summation]['count'] += 1

                cpu = 0.0
                for pid in stat_dict:
                    if pid != summation:
                        cpu += stat_dict[pid]['cpu-curr']
                stat_dict[summation]['cpu-curr'] = cpu

                if cpu < stat_dict[summation]['min']:
                    stat_dict[summation]['min'] = cpu
                if cpu > stat_dict[summation]['max']:
                    stat_dict[summation]['max'] = cpu

                stat_dict[summation]['total'] += cpu
                stat_dict[summation]['avg'] = stat_dict[summation]['total'] / stat_dict[summation]['count']
            # end if
        # end for

        cpu = 0.0
        for pid in stat_dict:
            if pid != summation:
                cpu += stat_dict[pid]['cpu-curr']
        stat_dict[summation]['cpu-curr'] = cpu

        if cpu < stat_dict[summation]['min']:
            stat_dict[summation]['min'] = cpu
        if cpu > stat_dict[summation]['max']:
            stat_dict[summation]['max'] = cpu

        stat_dict[summation]['total'] += cpu
        stat_dict[summation]['avg'] = stat_dict[summation]['total'] / stat_dict[summation]['count']


    print("--------------Stats-----------------")
    for pid in stat_dict:
        print("----------------------------------")
        print(pid)
        for j in stat_dict[pid]:
            print(j, ": ", stat_dict[pid][j])
    
if __name__ == '__main__':
    pid = sys.argv[1]
    pattern = sys.argv[2]
    time_run = sys.argv[3]
    command = "pidstat -p " + pid + " -t | grep '" + pattern + "' | awk '{print $5}'"
    print(command)
    result = subprocess.run(command, stdout=subprocess.PIPE, shell=True)
    list_pid = result.stdout.decode('utf-8').split()
    print(list_pid)

    grep_pid = ""
    for i in list_pid:
        grep_pid += " -p " + i
    command = "top -b -d 1 -n " + time_run + grep_pid + " > result.txt"
    print(command)
    subprocess.run(command, shell=True)

    check_log(list_pid)