package com.autotune.arabic.engine;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * يكتب الصوت المُعالَج إلى ملف WAV على الجهاز.
 *
 * يُفتح الملف عند بدء التسجيل، وتُكتب العينات بشكل متدفق،
 * ثم عند الإيقاف يُحدَّث رأس WAV بالحجم الفعلي.
 */
public class RecordingWriter {

    private static final int SAMPLE_RATE   = AudioEngine.SAMPLE_RATE;
    private static final int CHANNELS      = 1;
    private static final int BITS          = 16;
    private static final int BYTE_RATE     = SAMPLE_RATE * CHANNELS * BITS / 8;
    private static final int BLOCK_ALIGN   = CHANNELS * BITS / 8;

    private File outputFile;
    private FileOutputStream fos;
    private long dataBytes = 0;
    private volatile boolean active = false;

    /**
     * يبدأ تسجيلاً جديداً في المجلد المحدد.
     * @return الملف الذي سيُكتب فيه التسجيل
     */
    public File start(File dir) throws IOException {
        if (active) stop();

        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        outputFile = new File(dir, "AutoTune_" + ts + ".wav");
        fos = new FileOutputStream(outputFile);
        dataBytes = 0;
        writeWavHeader(fos, 0); // نكتب رأساً مؤقتاً بحجم صفر
        active = true;
        return outputFile;
    }

    /** يُكتب بلوك عينات صوتية (short) في الملف */
    public void write(short[] samples, int count) {
        if (!active || fos == null) return;
        byte[] buf = new byte[count * 2];
        for (int i = 0; i < count; i++) {
            buf[i * 2]     = (byte) (samples[i] & 0xFF);
            buf[i * 2 + 1] = (byte) ((samples[i] >> 8) & 0xFF);
        }
        try {
            fos.write(buf, 0, buf.length);
            dataBytes += buf.length;
        } catch (IOException ignored) {}
    }

    /**
     * يُوقف التسجيل ويُحدّث رأس WAV بالحجم الصحيح.
     * @return الملف المكتمل، أو null إذا لم يكن هناك تسجيل
     */
    public File stop() {
        if (!active) return null;
        active = false;
        try {
            fos.flush();
            fos.close();
            // نُحدّث حقلَي الحجم في رأس WAV باستخدام RandomAccessFile
            try (RandomAccessFile raf = new RandomAccessFile(outputFile, "rw")) {
                // حجم RIFF الكلي = 36 + dataBytes
                raf.seek(4);
                writeInt32LE(raf, (int)(36 + dataBytes));
                // حجم data chunk
                raf.seek(40);
                writeInt32LE(raf, (int) dataBytes);
            }
        } catch (IOException ignored) {}
        return outputFile;
    }

    public boolean isActive() { return active; }

    // ─── مساعدات WAV ─────────────────────────────────────────────────

    private void writeWavHeader(FileOutputStream out, long dataSize) throws IOException {
        // RIFF chunk
        out.write(new byte[]{'R','I','F','F'});
        writeInt32LE(out, (int)(36 + dataSize));
        out.write(new byte[]{'W','A','V','E'});
        // fmt sub-chunk
        out.write(new byte[]{'f','m','t',' '});
        writeInt32LE(out, 16);         // حجم fmt chunk
        writeInt16LE(out, 1);          // PCM = 1
        writeInt16LE(out, CHANNELS);
        writeInt32LE(out, SAMPLE_RATE);
        writeInt32LE(out, BYTE_RATE);
        writeInt16LE(out, BLOCK_ALIGN);
        writeInt16LE(out, BITS);
        // data sub-chunk
        out.write(new byte[]{'d','a','t','a'});
        writeInt32LE(out, (int) dataSize);
    }

    private void writeInt32LE(FileOutputStream out, int v) throws IOException {
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
        out.write((v >> 16) & 0xFF);
        out.write((v >> 24) & 0xFF);
    }

    private void writeInt16LE(FileOutputStream out, int v) throws IOException {
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
    }

    private void writeInt32LE(RandomAccessFile raf, int v) throws IOException {
        raf.write(v & 0xFF);
        raf.write((v >> 8) & 0xFF);
        raf.write((v >> 16) & 0xFF);
        raf.write((v >> 24) & 0xFF);
    }
}
