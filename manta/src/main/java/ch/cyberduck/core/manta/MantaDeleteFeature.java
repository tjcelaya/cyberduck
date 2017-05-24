package ch.cyberduck.core.manta;

/*
 * Copyright (c) 2002-2017 iterate GmbH. All rights reserved.
 * https://cyberduck.io/
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

import ch.cyberduck.core.DefaultIOExceptionMappingService;
import ch.cyberduck.core.LoginCallback;
import ch.cyberduck.core.Path;
import ch.cyberduck.core.PathContainerService;
import ch.cyberduck.core.exception.BackgroundException;
import ch.cyberduck.core.features.Delete;

import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.List;

import com.joyent.manta.exception.MantaException;
import com.joyent.manta.exception.MantaIOException;

public class MantaDeleteFeature implements Delete {

    public static final Logger log = Logger.getLogger(MantaDeleteFeature.class);

    private final MantaSession session;

    public MantaDeleteFeature(MantaSession session) {
        this.session = session;
    }

    @Override
    public void delete(final List<Path> files, final LoginCallback prompt, final Callback callback) throws BackgroundException {
        for(Path file : files) {
            callback.delete(file);
            log.error("deleteing file " + file);
            try {
                // TODO: verify deleteRecursive behavior
                session.getClient().deleteRecursive(session.pathMapper.requestPath(file));
            }
            catch(MantaException | MantaIOException e) {
                throw new MantaExceptionMappingService().map("Cannot delete {0}", e, file);
            }
            catch(IOException e) {
                throw new DefaultIOExceptionMappingService().map("Cannot delete {0}", e, file);
            }
        }
    }

    @Override
    public boolean isSupported(final Path file) {
        return session.pathMapper.isUserWritable(file);
    }

    @Override
    public boolean isRecursive() {
        return true;
    }
}
