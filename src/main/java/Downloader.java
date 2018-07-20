import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;

public class Downloader {

    public void d() throws IOException {
        BufferedInputStream in = new BufferedInputStream(new URL("http://www2.sunat.gob.pe/padron_reducido_ruc.zip").openStream());
    }

    public static void main(String[] args) throws IOException {
        URL url = new URL("https://www.dropbox.com/s/542ffcu2kqsqywq/dual%20boot.txt?dl=0");
        ReadableByteChannel readableByteChannel = Channels.newChannel(url.openStream());

        FileOutputStream fileOutputStream = new FileOutputStream("ruc.txt");
        FileChannel fileChannel = fileOutputStream.getChannel();

        fileOutputStream.getChannel().transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
    }
}
