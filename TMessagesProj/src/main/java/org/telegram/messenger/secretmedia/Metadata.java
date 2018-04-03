package org.telegram.messenger.secretmedia;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Arrays;
import java.util.List;

/**
 * A collection of metadata entries.
 */
public final class Metadata implements Parcelable {

    /**
     * A metadata entry.
     */
    public interface Entry extends Parcelable {}

    private final Entry[] entries;

    /**
     * @param entries The metadata entries.
     */
    public Metadata(Entry... entries) {
        this.entries = entries == null ? new Entry[0] : entries;
    }

    /**
     * @param entries The metadata entries.
     */
    public Metadata(List<? extends Entry> entries) {
        if (entries != null) {
            this.entries = new Entry[entries.size()];
            entries.toArray(this.entries);
        } else {
            this.entries = new Entry[0];
        }
    }

    /* package */ Metadata(Parcel in) {
        entries = new Metadata.Entry[in.readInt()];
        for (int i = 0; i < entries.length; i++) {
            entries[i] = in.readParcelable(Entry.class.getClassLoader());
        }
    }

    /**
     * Returns the number of metadata entries.
     */
    public int length() {
        return entries.length;
    }

    /**
     * Returns the entry at the specified index.
     *
     * @param index The index of the entry.
     * @return The entry at the specified index.
     */
    public Metadata.Entry get(int index) {
        return entries[index];
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Metadata other = (Metadata) obj;
        return Arrays.equals(entries, other.entries);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(entries);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(entries.length);
        for (Entry entry : entries) {
            dest.writeParcelable(entry, 0);
        }
    }

    public static final Parcelable.Creator<Metadata> CREATOR = new Parcelable.Creator<Metadata>() {
        @Override
        public Metadata createFromParcel(Parcel in) {
            return new Metadata(in);
        }

        @Override
        public Metadata[] newArray(int size) {
            return new Metadata[0];
        }
    };

}
