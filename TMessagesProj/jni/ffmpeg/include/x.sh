find . -regex '.*\.[ch(cc)]' -print0 | while IFS= read -r -d '' file
do
    echo "$file"
    cat x > $file
done
