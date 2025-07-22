import sys

pid = ""
def check_log():
    path = "result.txt"
    min = 100000
    max = -1
    total = 0.0
    count = 0

    with open(path) as file:
        for line in file:
            if pid in line:
                count += 1
                tmp = line.split()[8].strip()
                cpu = float(tmp)
                print("cpu: ", cpu)
                
                if cpu < min:
                    min = cpu
                if cpu > max:
                    max = cpu
                total += cpu

    avg = total / count
    print("--------------Stats-----------------")
    print("count: ", count)
    print("max: ", max)
    print("min: ", min)
    print("avg: ", avg)

if __name__ == '__main__':
    pid = sys.argv[1]
    check_log()