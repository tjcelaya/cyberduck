package ch.cyberduck.core.azure;

import ch.cyberduck.core.AbstractTestCase;
import ch.cyberduck.core.Cache;
import ch.cyberduck.core.Credentials;
import ch.cyberduck.core.DisabledHostKeyCallback;
import ch.cyberduck.core.DisabledLoginController;
import ch.cyberduck.core.DisabledPasswordStore;
import ch.cyberduck.core.DisabledProgressListener;
import ch.cyberduck.core.DisabledTranscriptListener;
import ch.cyberduck.core.Host;
import ch.cyberduck.core.LoginConnectionService;
import ch.cyberduck.core.Path;

import org.junit.Test;

import java.util.Collections;
import java.util.EnumSet;
import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @version $Id$
 */
public class AzureDirectoryFeatureTest extends AbstractTestCase {

    @Test
    public void testCreateContainer() throws Exception {
        final Host host = new Host(new AzureProtocol(), "cyberduck.blob.core.windows.net", new Credentials(
                properties.getProperty("azure.account"), properties.getProperty("azure.key")
        ));
        final AzureSession session = new AzureSession(host);
        new LoginConnectionService(new DisabledLoginController(), new DisabledHostKeyCallback(),
                new DisabledPasswordStore(), new DisabledProgressListener(), new DisabledTranscriptListener()).connect(session, Cache.<Path>empty());
        final Path container = new Path(UUID.randomUUID().toString(), EnumSet.of(Path.Type.directory));
        new AzureDirectoryFeature(session).mkdir(container, null);
        assertTrue(new AzureFindFeature(session).find(container));
        new AzureDeleteFeature(session).delete(Collections.<Path>singletonList(container), new DisabledLoginController(), new DisabledProgressListener());
        assertFalse(new AzureFindFeature(session).find(container));
    }


    @Test
    public void testCreatePlaceholder() throws Exception {
        final Host host = new Host(new AzureProtocol(), "cyberduck.blob.core.windows.net", new Credentials(
                properties.getProperty("azure.account"), properties.getProperty("azure.key")
        ));
        final AzureSession session = new AzureSession(host);
        new LoginConnectionService(new DisabledLoginController(), new DisabledHostKeyCallback(),
                new DisabledPasswordStore(), new DisabledProgressListener(), new DisabledTranscriptListener()).connect(session, Cache.<Path>empty());
        final Path container = new Path("/cyberduck", EnumSet.of(Path.Type.volume, Path.Type.directory));
        final Path placeholder = new Path(container, UUID.randomUUID().toString(),
                EnumSet.of(Path.Type.directory, Path.Type.placeholder));
        new AzureDirectoryFeature(session).mkdir(placeholder, null);
        assertTrue(new AzureFindFeature(session).find(placeholder));
        new AzureDeleteFeature(session).delete(Collections.<Path>singletonList(placeholder), new DisabledLoginController(), new DisabledProgressListener());
        assertFalse(new AzureFindFeature(session).find(placeholder));
    }
}
