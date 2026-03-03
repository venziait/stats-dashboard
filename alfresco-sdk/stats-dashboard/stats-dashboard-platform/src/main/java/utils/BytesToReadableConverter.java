package utils;

public class BytesToReadableConverter {
    static long kilo = 1024;
    static long mega = kilo * kilo;
    static long giga = mega * kilo;
    static long tera = giga * kilo;

    public static double getSizeInGb(long sizeInBytes){
        double kb = (double)sizeInBytes / kilo;
        double mb = kb / kilo;
        double gb = mb / kilo;
        return gb;
    }

    public static String getReadableSize(long sizeInBytes){
        String s = "";
        double kb = (double)sizeInBytes / kilo;
        double mb = kb / kilo;
        double gb = mb / kilo;
        double tb = gb / kilo;
        if(sizeInBytes < kilo) {
            s = sizeInBytes + " Bytes";
        } else if(sizeInBytes >= kilo && sizeInBytes < mega) {
            s =  String.format("%.2f", kb) + " KB";
        } else if(sizeInBytes >= mega && sizeInBytes < giga) {
            s = String.format("%.2f", mb) + " MB";
        } else if(sizeInBytes >= giga && sizeInBytes < tera) {
            s = String.format("%.2f", gb) + " GB";
        } else if(sizeInBytes >= tera) {
            s = String.format("%.2f", tb) + " TB";
        }
        return s;
    }
}
