find . -regex '.*\.[ch(cpp)]' -print0 | while IFS= read -r -d '' file
do
    echo "$file"
    cat > "$file"
done
