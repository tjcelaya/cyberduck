package ch.cyberduck.core.vault;

/*
 * Copyright (c) 2002-2016 iterate GmbH. All rights reserved.
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

import ch.cyberduck.core.PasswordCallback;
import ch.cyberduck.core.Session;
import ch.cyberduck.core.exception.BackgroundException;
import ch.cyberduck.core.features.Vault;

import org.apache.log4j.Logger;

public class LoadingVaultLookupListener implements VaultLookupListener {
    private static final Logger log = Logger.getLogger(LoadingVaultLookupListener.class);

    private final Session<?> session;
    private final VaultRegistry registry;
    private final PasswordCallback prompt;

    public LoadingVaultLookupListener(final Session<?> session, final VaultRegistry registry, final PasswordCallback prompt) {
        this.session = session;
        this.registry = registry;
        this.prompt = prompt;
    }

    @Override
    public void found(final Vault vault) throws VaultUnlockCancelException {
        synchronized(registry) {
            if(registry.contains(vault)) {
                log.warn(String.format("Ignore vault %s found already loaded", vault));
                return;
            }
            if(log.isInfoEnabled()) {
                log.info(String.format("Loading vault %s for session %s", vault, session));
            }
            try {
                registry.found(vault.load(session, prompt));
            }
            catch(BackgroundException e) {
                log.warn(String.format("Failure loading vault %s. %s", vault, e.getDetail()));
                throw new VaultUnlockCancelException(vault);
            }
        }
    }
}
