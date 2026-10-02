package dev.graphnous.scanner.listener;

public interface ScanProcessListener {

    void stdout(String line);

    void stderr(String line);
}
