timeout=8

make -C /Users/celinecamacho/Desktop/student_21_pop/be1-go/pop pop
/Users/celinecamacho/Desktop/student_21_pop/be1-go/pop organizer --pk 'J9fBzJV70Jk5c-i3277Uq4CmeL4t53WDfUghaK0HpeM=' serve >> messages.log &

pid=` jobs -p`
sleep $timeout
kill -n SIGTERM $pid