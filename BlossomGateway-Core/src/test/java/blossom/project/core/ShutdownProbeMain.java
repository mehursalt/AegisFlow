package blossom.project.core;
/** Native acceptance harness only. stdin EOF/byte triggers the real JVM shutdown hook. */
public final class ShutdownProbeMain {
    public static void main(String[] args) throws Exception {
        Bootstrap.main(args);
        System.in.read();
        System.exit(0);
    }
}
