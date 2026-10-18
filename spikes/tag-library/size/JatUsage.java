// Usage stub so R8 keeps exactly what the app would call (read, write, multi-value, artwork, lyrics).
import java.io.File;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagOptionSingleton;
import org.jaudiotagger.tag.flac.FlacTag;
import org.jaudiotagger.tag.images.Artwork;
import org.jaudiotagger.tag.images.ArtworkFactory;

public final class JatUsage {
    public static void main(String[] args) throws Exception {
        TagOptionSingleton.getInstance().setAndroid(true);
        AudioFile f = AudioFileIO.read(new File(args[0]));
        Tag tag = f.getTagOrCreateAndSetDefault();
        System.out.println(tag.getAll(FieldKey.ARTIST) + tag.getFirst(FieldKey.LYRICS) + f.getAudioHeader().getTrackLength());
        tag.addField(FieldKey.ARTIST, args[1]);
        Artwork a = ArtworkFactory.getNew();
        a.setBinaryData(new byte[] {1});
        if (tag instanceof FlacTag) ((FlacTag) tag).setField(((FlacTag) tag).createArtworkField(new byte[] {1}, 3, "image/png", "", 1, 1, 24, 0));
        else tag.setField(a);
        Artwork first = tag.getFirstArtwork();
        if (first != null) System.out.println(first.getBinaryData().length);
        f.commit();
    }
}
