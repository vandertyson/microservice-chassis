top -b -d 1 -n $2 -p $1 > result.txt
python3 stats2.py $1
