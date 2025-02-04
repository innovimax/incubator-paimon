/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.paimon.io.cache;

import org.apache.paimon.memory.MemorySegment;
import org.apache.paimon.options.MemorySize;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/** Test for {@link CachedRandomInputView}. */
public class CachedRandomInputViewTest {

    @TempDir Path tempDir;

    private final ThreadLocalRandom rnd = ThreadLocalRandom.current();

    @Test
    public void testMatched() throws IOException {
        innerTest(1024 * 512);
    }

    @Test
    public void testNotMatched() throws IOException {
        innerTest(131092);
    }

    @Test
    public void testRandom() throws IOException {
        innerTest(rnd.nextInt(5000, 100000));
    }

    private void innerTest(int len) throws IOException {
        byte[] bytes = new byte[len];
        MemorySegment segment = MemorySegment.wrap(bytes);
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) rnd.nextInt();
        }

        File file = writeFile(bytes);
        CacheManager cacheManager = new CacheManager(1024, MemorySize.ofKibiBytes(128));
        CachedRandomInputView view = new CachedRandomInputView(file, cacheManager);

        // read first one
        view.setReadPosition(0);
        assertThat(view.readLong()).isEqualTo(segment.getLongBigEndian(0));

        // read mid
        int mid = bytes.length / 2;
        view.setReadPosition(mid);
        assertThat(view.readLong()).isEqualTo(segment.getLongBigEndian(mid));

        // read special
        view.setReadPosition(1021);
        assertThat(view.readLong()).isEqualTo(segment.getLongBigEndian(1021));

        // read last one
        view.setReadPosition(bytes.length - 1);
        assertThat(view.readByte()).isEqualTo(bytes[bytes.length - 1]);

        // random read
        for (int i = 0; i < 10000; i++) {
            int position = rnd.nextInt(bytes.length - 8);
            view.setReadPosition(position);
            assertThat(view.readLong()).isEqualTo(segment.getLongBigEndian(position));
        }

        view.close();
        assertThat(cacheManager.cache().size()).isEqualTo(0);
    }

    private File writeFile(byte[] bytes) throws IOException {
        File file = new File(tempDir.toFile(), UUID.randomUUID().toString());
        if (!file.createNewFile()) {
            throw new IOException("Can not create: " + file);
        }
        Files.write(file.toPath(), bytes, StandardOpenOption.WRITE);
        return file;
    }
}
