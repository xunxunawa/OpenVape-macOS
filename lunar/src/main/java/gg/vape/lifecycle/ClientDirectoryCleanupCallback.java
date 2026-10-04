package gg.vape.lifecycle;

import java.io.File;

public class ClientDirectoryCleanupCallback
implements ClientLifecycleCallback {
    @Override
    public void log(String message) {
    }

    public ClientDirectoryCleanupCallback() {
        // The port preserves local configs and reports; startup does not delete files.
    }


    @Override
    public void close() {
    }
}
