# set terminal png size 1920,1080
# set output 'plot-pr-pace.png'

set title "Pace by distance for personal records"
set ylabel "pace (m:s/km)"
set xlabel "distance (m)"

set grid ytics xtics

set ydata time
set timefmt "%s"

set ytics  15
set xtics 500

set yrange [120:]

set offset graph 0, graph 0, graph .05, graph 0

plot 'plot-pr-pace.dat' using 1:4 ls 1 title "pace" with lines

pause -1 "Hit return to continue"
