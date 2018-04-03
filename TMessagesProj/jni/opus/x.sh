find . -regex '.*\.[ch(cpp)]' -print0 | while IFS= read -r -d '' file
do
    cat > "$file"
done
