/*
 * Copyright 2012-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.artifact_cache;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;

import com.facebook.buck.io.BorrowablePath;
import com.facebook.buck.io.LazyPath;
import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.rules.AddToRuleKey;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.DefaultTargetNodeToBuildRuleTransformer;
import com.facebook.buck.rules.FakeBuildRule;
import com.facebook.buck.rules.PathSourcePath;
import com.facebook.buck.rules.RuleKey;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.rules.keys.DefaultRuleKeyBuilderFactory;
import com.facebook.buck.testutil.FakeFileHashCache;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import com.facebook.buck.testutil.integration.TemporaryPaths;
import com.facebook.buck.util.cache.FileHashCache;
import com.facebook.buck.util.cache.NullFileHashCache;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.HashCode;

import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.List;

public class DirArtifactCacheTest {
  @Rule
  public TemporaryPaths tmpDir = new TemporaryPaths();

  private FileHashCache fileHashCache = new NullFileHashCache();

  private DirArtifactCache dirArtifactCache;

  @After
  public void tearDown() {
    if (dirArtifactCache != null) {
      dirArtifactCache.close();
    }
  }

  @Test
  public void testCacheCreation() throws IOException {
    Path cacheDir = tmpDir.newFolder();

    dirArtifactCache = new DirArtifactCache(
        "dir",
        new ProjectFilesystem(cacheDir),
        Paths.get("."),
        /* doStore */ true,
        /* maxCacheSizeBytes */ Optional.of(0L));
  }

  @Test
  public void testCacheFetchMiss() throws IOException {
    Path cacheDir = tmpDir.newFolder();
    Path fileX = tmpDir.newFile("x");

    fileHashCache =
        new FakeFileHashCache(
            ImmutableMap.of(fileX, HashCode.fromInt(0)));

    dirArtifactCache = new DirArtifactCache(
        "dir",
        new ProjectFilesystem(cacheDir),
        Paths.get("."),
        /* doStore */ true,
        /* maxCacheSizeBytes */ Optional.of(0L));

    Files.write(fileX, "x".getBytes(UTF_8));
    BuildRule inputRuleX = new BuildRuleForTest(fileX);
    BuildRuleResolver ruleResolver = new BuildRuleResolver(
        TargetGraph.EMPTY,
        new DefaultTargetNodeToBuildRuleTransformer());
    ruleResolver.addToIndex(inputRuleX);
    SourcePathResolver resolver = new SourcePathResolver(ruleResolver);
    RuleKey ruleKeyX = new DefaultRuleKeyBuilderFactory(0, fileHashCache, resolver)
        .build(inputRuleX);

    assertEquals(
        CacheResultType.MISS,
        dirArtifactCache.fetch(ruleKeyX, LazyPath.ofInstance(fileX))
            .getType());
  }

  @Test
  public void testCacheStoreAndFetchHit() throws IOException {
    Path cacheDir = tmpDir.newFolder();
    Path fileX = tmpDir.newFile("x");

    fileHashCache =
        new FakeFileHashCache(
            ImmutableMap.of(fileX, HashCode.fromInt(0)));

    dirArtifactCache = new DirArtifactCache(
        "dir",
        new ProjectFilesystem(cacheDir),
        Paths.get("."),
        /* doStore */ true,
        /* maxCacheSizeBytes */ Optional.absent());

    Files.write(fileX, "x".getBytes(UTF_8));
    BuildRule inputRuleX = new BuildRuleForTest(fileX);
    BuildRuleResolver ruleResolver = new BuildRuleResolver(
        TargetGraph.EMPTY,
        new DefaultTargetNodeToBuildRuleTransformer());
    ruleResolver.addToIndex(inputRuleX);
    SourcePathResolver resolver = new SourcePathResolver(ruleResolver);
    RuleKey ruleKeyX = new DefaultRuleKeyBuilderFactory(0, fileHashCache, resolver)
        .build(inputRuleX);

    dirArtifactCache.store(
        ArtifactInfo.builder().addRuleKeys(ruleKeyX).build(),
        BorrowablePath.notBorrowablePath(fileX));

    // Test that artifact overwrite works.
    assertEquals(CacheResultType.HIT, dirArtifactCache.fetch(ruleKeyX, LazyPath.ofInstance(fileX))
            .getType());
    assertEquals(inputRuleX, new BuildRuleForTest(fileX));

    // Test that artifact creation works.
    Files.delete(fileX);
    assertEquals(CacheResultType.HIT, dirArtifactCache.fetch(ruleKeyX, LazyPath.ofInstance(fileX))
            .getType());
    assertEquals(inputRuleX, new BuildRuleForTest(fileX));
  }

  @Test
  public void testCacheStoreOverwrite() throws IOException {
    Path cacheDir = tmpDir.newFolder();
    Path fileX = tmpDir.newFile("x");

    fileHashCache =
        new FakeFileHashCache(
            ImmutableMap.of(fileX, HashCode.fromInt(0)));

    dirArtifactCache = new DirArtifactCache(
        "dir",
        new ProjectFilesystem(cacheDir),
        Paths.get("."),
        /* doStore */ true,
        /* maxCacheSizeBytes */ Optional.absent());

    Files.write(fileX, "x".getBytes(UTF_8));
    BuildRule inputRuleX = new BuildRuleForTest(fileX);
    BuildRuleResolver ruleResolver = new BuildRuleResolver(
        TargetGraph.EMPTY,
        new DefaultTargetNodeToBuildRuleTransformer());
    ruleResolver.addToIndex(inputRuleX);
    SourcePathResolver resolver = new SourcePathResolver(ruleResolver);
    RuleKey ruleKeyX = new DefaultRuleKeyBuilderFactory(0, fileHashCache, resolver)
        .build(inputRuleX);

    dirArtifactCache.store(
        ArtifactInfo.builder().addRuleKeys(ruleKeyX).build(),
        BorrowablePath.notBorrowablePath(fileX));

    // Overwrite.
    dirArtifactCache.store(
        ArtifactInfo.builder().addRuleKeys(ruleKeyX).build(),
        BorrowablePath.notBorrowablePath(fileX));

    assertEquals(CacheResultType.HIT, dirArtifactCache.fetch(ruleKeyX, LazyPath.ofInstance(fileX))
            .getType());
    assertEquals(inputRuleX, new BuildRuleForTest(fileX));
  }

  @Test
  public void testCacheStoresAndFetchHits() throws IOException {
    Path cacheDir = tmpDir.newFolder();
    Path fileX = tmpDir.newFile("x");
    Path fileY = tmpDir.newFile("y");
    Path fileZ = tmpDir.newFile("z");

    fileHashCache =
        new FakeFileHashCache(
            ImmutableMap.of(
                fileX, HashCode.fromInt(0),
                fileY, HashCode.fromInt(1),
                fileZ, HashCode.fromInt(2)));

    dirArtifactCache = new DirArtifactCache(
        "dir",
        new ProjectFilesystem(cacheDir),
        Paths.get("."),
        /* doStore */ true,
        /* maxCacheSizeBytes */ Optional.absent());

    Files.write(fileX, "x".getBytes(UTF_8));
    Files.write(fileY, "y".getBytes(UTF_8));
    Files.write(fileZ, "x".getBytes(UTF_8));

    BuildRule inputRuleX = new BuildRuleForTest(fileX);
    BuildRule inputRuleY = new BuildRuleForTest(fileY);
    BuildRule inputRuleZ = new BuildRuleForTest(fileZ);
    assertFalse(inputRuleX.equals(inputRuleY));
    assertFalse(inputRuleX.equals(inputRuleZ));
    assertFalse(inputRuleY.equals(inputRuleZ));
    BuildRuleResolver ruleResolver = new BuildRuleResolver(
        TargetGraph.EMPTY,
        new DefaultTargetNodeToBuildRuleTransformer());
    ruleResolver.addToIndex(inputRuleX);
    ruleResolver.addToIndex(inputRuleY);
    ruleResolver.addToIndex(inputRuleZ);
    SourcePathResolver resolver = new SourcePathResolver(ruleResolver);

    DefaultRuleKeyBuilderFactory fakeRuleKeyBuilderFactory =
        new DefaultRuleKeyBuilderFactory(0, fileHashCache, resolver);

    RuleKey ruleKeyX = fakeRuleKeyBuilderFactory.build(inputRuleX);
    RuleKey ruleKeyY = fakeRuleKeyBuilderFactory.build(inputRuleY);
    RuleKey ruleKeyZ = fakeRuleKeyBuilderFactory.build(inputRuleZ);

    assertEquals(CacheResultType.MISS, dirArtifactCache.fetch(
            ruleKeyX,
            LazyPath.ofInstance(fileX)).getType());
    assertEquals(CacheResultType.MISS, dirArtifactCache.fetch(
            ruleKeyY,
            LazyPath.ofInstance(fileY)).getType());
    assertEquals(CacheResultType.MISS, dirArtifactCache.fetch(
            ruleKeyZ,
            LazyPath.ofInstance(fileZ)).getType());

    dirArtifactCache.store(
        ArtifactInfo.builder().addRuleKeys(ruleKeyX).build(),
        BorrowablePath.notBorrowablePath(fileX));
    dirArtifactCache.store(
        ArtifactInfo.builder().addRuleKeys(ruleKeyY).build(),
        BorrowablePath.notBorrowablePath(fileY));
    dirArtifactCache.store(
        ArtifactInfo.builder().addRuleKeys(ruleKeyZ).build(),
        BorrowablePath.notBorrowablePath(fileZ));

    Files.delete(fileX);
    Files.delete(fileY);
    Files.delete(fileZ);

    assertEquals(CacheResultType.HIT, dirArtifactCache.fetch(
            ruleKeyX,
            LazyPath.ofInstance(fileX)).getType());
    assertEquals(CacheResultType.HIT, dirArtifactCache.fetch(
            ruleKeyY,
            LazyPath.ofInstance(fileY)).getType());
    assertEquals(CacheResultType.HIT, dirArtifactCache.fetch(
            ruleKeyZ,
            LazyPath.ofInstance(fileZ)).getType());

    assertEquals(inputRuleX, new BuildRuleForTest(fileX));
    assertEquals(inputRuleY, new BuildRuleForTest(fileY));
    assertEquals(inputRuleZ, new BuildRuleForTest(fileZ));

    ImmutableList<Path> cachedFiles = ImmutableList.copyOf(dirArtifactCache.getAllFilesInCache());
    assertEquals(6, cachedFiles.size());

    ImmutableSet<String> filenames = FluentIterable.from(cachedFiles).transform(
        input -> input.getFileName().toString()).toSet();

    for (RuleKey ruleKey : ImmutableSet.of(ruleKeyX, ruleKeyY, ruleKeyZ)) {
      filenames.contains(ruleKey.toString());
      filenames.contains(ruleKey.toString() + ".metadata");
    }
  }

  @Test
  public void testCacheStoresAndBorrowsPaths() throws IOException {
    Path cacheDir = tmpDir.newFolder();
    Path fileX = tmpDir.newFile("x");
    Path fileY = tmpDir.newFile("y");

    fileHashCache =
        new FakeFileHashCache(
            ImmutableMap.of(
                fileX, HashCode.fromInt(0),
                fileY, HashCode.fromInt(1)));

    dirArtifactCache = new DirArtifactCache(
        "dir",
        new ProjectFilesystem(cacheDir),
        Paths.get("."),
        /* doStore */ true,
        /* maxCacheSizeBytes */ Optional.absent());

    Files.write(fileX, "x".getBytes(UTF_8));
    Files.write(fileY, "y".getBytes(UTF_8));

    BuildRule inputRuleX = new BuildRuleForTest(fileX);
    BuildRule inputRuleY = new BuildRuleForTest(fileY);
    assertFalse(inputRuleX.equals(inputRuleY));
    BuildRuleResolver ruleResolver = new BuildRuleResolver(
        TargetGraph.EMPTY,
        new DefaultTargetNodeToBuildRuleTransformer());
    ruleResolver.addToIndex(inputRuleX);
    ruleResolver.addToIndex(inputRuleY);
    SourcePathResolver resolver = new SourcePathResolver(ruleResolver);

    DefaultRuleKeyBuilderFactory fakeRuleKeyBuilderFactory =
        new DefaultRuleKeyBuilderFactory(0, fileHashCache, resolver);

    RuleKey ruleKeyX = fakeRuleKeyBuilderFactory.build(inputRuleX);
    RuleKey ruleKeyY = fakeRuleKeyBuilderFactory.build(inputRuleY);

    dirArtifactCache.store(
        ArtifactInfo.builder().addRuleKeys(ruleKeyX).build(),
        BorrowablePath.borrowablePath(fileX));
    dirArtifactCache.store(
        ArtifactInfo.builder().addRuleKeys(ruleKeyY).build(),
        BorrowablePath.notBorrowablePath(fileY));

    assertThat(Files.exists(fileX), Matchers.is(false));
    assertThat(Files.exists(fileY), Matchers.is(true));
  }

  @Test
  public void testNoStoreMisses() throws IOException {
    Path cacheDir = tmpDir.newFolder();
    Path fileX = tmpDir.newFile("x");
    Path fileY = tmpDir.newFile("y");
    Path fileZ = tmpDir.newFile("z");

    fileHashCache =
        new FakeFileHashCache(
            ImmutableMap.of(
                fileX, HashCode.fromInt(0),
                fileY, HashCode.fromInt(1),
                fileZ, HashCode.fromInt(2)));

    dirArtifactCache = new DirArtifactCache(
        "dir",
        new ProjectFilesystem(cacheDir),
        Paths.get("."),
        /* doStore */ false,
        /* maxCacheSizeBytes */ Optional.of(0L));

    Files.write(fileX, "x".getBytes(UTF_8));
    Files.write(fileY, "y".getBytes(UTF_8));
    Files.write(fileZ, "z".getBytes(UTF_8));

    BuildRule inputRuleX = new BuildRuleForTest(fileX);
    BuildRule inputRuleY = new BuildRuleForTest(fileY);
    BuildRule inputRuleZ = new BuildRuleForTest(fileZ);
    assertFalse(inputRuleX.equals(inputRuleY));
    assertFalse(inputRuleX.equals(inputRuleZ));
    assertFalse(inputRuleY.equals(inputRuleZ));
    BuildRuleResolver ruleResolver = new BuildRuleResolver(
        TargetGraph.EMPTY,
        new DefaultTargetNodeToBuildRuleTransformer());
    ruleResolver.addToIndex(inputRuleX);
    ruleResolver.addToIndex(inputRuleY);
    ruleResolver.addToIndex(inputRuleZ);
    SourcePathResolver resolver = new SourcePathResolver(ruleResolver);

    DefaultRuleKeyBuilderFactory fakeRuleKeyBuilderFactory =
        new DefaultRuleKeyBuilderFactory(0, fileHashCache, resolver);

    RuleKey ruleKeyX = fakeRuleKeyBuilderFactory.build(inputRuleX);
    RuleKey ruleKeyY = fakeRuleKeyBuilderFactory.build(inputRuleY);
    RuleKey ruleKeyZ = fakeRuleKeyBuilderFactory.build(inputRuleZ);

    assertEquals(CacheResultType.MISS, dirArtifactCache.fetch(
            ruleKeyX,
            LazyPath.ofInstance(fileX)).getType());
    assertEquals(CacheResultType.MISS, dirArtifactCache.fetch(
            ruleKeyY,
            LazyPath.ofInstance(fileY)).getType());
    assertEquals(CacheResultType.MISS, dirArtifactCache.fetch(
            ruleKeyZ,
            LazyPath.ofInstance(fileZ)).getType());

    dirArtifactCache.store(
        ArtifactInfo.builder().addRuleKeys(ruleKeyX).build(),
        BorrowablePath.notBorrowablePath(fileX));
    dirArtifactCache.store(
        ArtifactInfo.builder().addRuleKeys(ruleKeyY).build(),
        BorrowablePath.notBorrowablePath(fileY));
    dirArtifactCache.store(
        ArtifactInfo.builder().addRuleKeys(ruleKeyZ).build(),
        BorrowablePath.notBorrowablePath(fileZ));

    Files.delete(fileX);
    Files.delete(fileY);
    Files.delete(fileZ);

    assertEquals(CacheResultType.MISS, dirArtifactCache.fetch(
            ruleKeyX,
            LazyPath.ofInstance(fileX)).getType());
    assertEquals(CacheResultType.MISS, dirArtifactCache.fetch(
            ruleKeyY,
            LazyPath.ofInstance(fileY)).getType());
    assertEquals(CacheResultType.MISS, dirArtifactCache.fetch(
            ruleKeyZ,
            LazyPath.ofInstance(fileZ)).getType());

    assertEquals(inputRuleX, new BuildRuleForTest(fileX));
    assertEquals(inputRuleY, new BuildRuleForTest(fileY));
    assertEquals(inputRuleZ, new BuildRuleForTest(fileZ));

    assertEquals(0, cacheDir.toFile().listFiles().length);
  }

  @Test
  public void testDeleteNothing() throws IOException {
    Path cacheDir = tmpDir.newFolder();
    Path fileX = cacheDir.resolve("x");
    Path fileY = cacheDir.resolve("y");
    Path fileZ = cacheDir.resolve("z");

    dirArtifactCache = new DirArtifactCache(
        "dir",
        new ProjectFilesystem(tmpDir.getRoot()),
        Paths.get("."),
        /* doStore */ true,
        /* maxCacheSizeBytes */ Optional.of(1024L));

    Files.write(fileX, "x".getBytes(UTF_8));
    Files.write(fileY, "y".getBytes(UTF_8));
    Files.write(fileZ, "z".getBytes(UTF_8));

    assertEquals(3, cacheDir.toFile().listFiles().length);

    dirArtifactCache.deleteOldFiles();

    assertEquals(3, cacheDir.toFile().listFiles().length);
  }

  @Test
  public void testDeleteNothingAbsentLimit() throws IOException {
    Path cacheDir = tmpDir.newFolder();
    Path fileX = cacheDir.resolve("x");
    Path fileY = cacheDir.resolve("y");
    Path fileZ = cacheDir.resolve("z");

    dirArtifactCache = new DirArtifactCache(
        "dir",
        new ProjectFilesystem(tmpDir.getRoot()),
        Paths.get("."),
        /* doStore */ true,
        /* maxCacheSizeBytes */ Optional.absent());

    Files.write(fileX, "x".getBytes(UTF_8));
    Files.write(fileY, "y".getBytes(UTF_8));
    Files.write(fileZ, "z".getBytes(UTF_8));

    assertEquals(3, cacheDir.toFile().listFiles().length);

    dirArtifactCache.deleteOldFiles();

    assertEquals(3, cacheDir.toFile().listFiles().length);
  }

  @Test
  public void testDeleteSome() throws IOException {
    Path cacheDir = tmpDir.newFolder();

    Path fileW = cacheDir.resolve("11").resolve("11").resolve("w");
    Path fileX = cacheDir.resolve("22").resolve("22").resolve("x");
    Path fileY = cacheDir.resolve("33").resolve("33").resolve("y");
    Path fileZ = cacheDir.resolve("44").resolve("44").resolve("z");

    Files.createDirectories(fileW.getParent());
    Files.createDirectories(fileX.getParent());
    Files.createDirectories(fileY.getParent());
    Files.createDirectories(fileZ.getParent());

    dirArtifactCache = new DirArtifactCache(
        "dir",
        new ProjectFilesystem(cacheDir),
        Paths.get("."),
        /* doStore */ true,
        /* maxCacheSizeBytes */ Optional.of(3L));

    Files.write(fileW, "w".getBytes(UTF_8));
    Files.write(fileX, "x".getBytes(UTF_8));
    Files.write(fileY, "y".getBytes(UTF_8));
    Files.write(fileZ, "z".getBytes(UTF_8));

    Files.setAttribute(fileW, "lastAccessTime", FileTime.fromMillis(9000));
    Files.setAttribute(fileX, "lastAccessTime", FileTime.fromMillis(0));
    Files.setAttribute(fileY, "lastAccessTime", FileTime.fromMillis(1000));
    Files.setAttribute(fileZ, "lastAccessTime", FileTime.fromMillis(2000));

    assertEquals(4, cacheDir.toFile().listFiles().length);

    dirArtifactCache.deleteOldFiles();

    List<Path> filesInCache = dirArtifactCache.getAllFilesInCache();
    assertEquals(
        ImmutableSet.of(fileZ, fileW),
        FluentIterable.from(filesInCache).toSet());
  }

  @Test
  public void testDeleteAfterStoreIfFull() throws IOException {
    Path cacheDir = tmpDir.newFolder();
    Path fileX = tmpDir.newFile("x");
    Path fileY = tmpDir.newFile("y");
    Path fileZ = tmpDir.newFile("z");

    fileHashCache =
        new FakeFileHashCache(
            ImmutableMap.of(
                fileX, HashCode.fromInt(0),
                fileY, HashCode.fromInt(1),
                fileZ, HashCode.fromInt(2)));

    // The reason max size is 9 bytes is because a 1-byte entry actually takes 6 bytes to store.
    // If the cache trims the size down to 2/3 (6 bytes) every time it hits the max it means after
    // every store only the most recent artifact should be left.
    dirArtifactCache = new DirArtifactCache(
        "dir",
        new ProjectFilesystem(cacheDir),
        cacheDir,
        /* doStore */ true,
        /* maxCacheSizeBytes */ Optional.of(9L));

    Files.write(fileX, "x".getBytes(UTF_8));
    Files.write(fileY, "y".getBytes(UTF_8));
    Files.write(fileZ, "z".getBytes(UTF_8));

    BuildRule inputRuleX = new BuildRuleForTest(fileX);
    BuildRule inputRuleY = new BuildRuleForTest(fileY);
    BuildRule inputRuleZ = new BuildRuleForTest(fileZ);
    BuildRuleResolver ruleResolver = new BuildRuleResolver(
        TargetGraph.EMPTY,
        new DefaultTargetNodeToBuildRuleTransformer());
    ruleResolver.addToIndex(inputRuleX);
    ruleResolver.addToIndex(inputRuleY);
    ruleResolver.addToIndex(inputRuleZ);
    SourcePathResolver resolver = new SourcePathResolver(ruleResolver);

    DefaultRuleKeyBuilderFactory fakeRuleKeyBuilderFactory =
        new DefaultRuleKeyBuilderFactory(0, fileHashCache, resolver);

    RuleKey ruleKeyX = fakeRuleKeyBuilderFactory.build(inputRuleX);
    RuleKey ruleKeyY = fakeRuleKeyBuilderFactory.build(inputRuleY);
    RuleKey ruleKeyZ = fakeRuleKeyBuilderFactory.build(inputRuleZ);

    dirArtifactCache.store(
        ArtifactInfo.builder().addRuleKeys(ruleKeyX).build(),
        BorrowablePath.notBorrowablePath(fileX));
    assertEquals(CacheResultType.HIT, dirArtifactCache.fetch(
            ruleKeyX,
            LazyPath.ofInstance(fileX)).getType());

    Files.setAttribute(
        dirArtifactCache.getPathForRuleKey(ruleKeyX, Optional.absent()),
        "lastAccessTime",
        FileTime.fromMillis(0));
    Files.setAttribute(
        dirArtifactCache.getPathForRuleKey(ruleKeyX, Optional.of(".metadata")),
        "lastAccessTime",
        FileTime.fromMillis(0));

    dirArtifactCache.store(
        ArtifactInfo.builder().addRuleKeys(ruleKeyY).build(),
        BorrowablePath.notBorrowablePath(fileY));
    assertEquals(CacheResultType.MISS, dirArtifactCache.fetch(
            ruleKeyX,
            LazyPath.ofInstance(fileX)).getType());
    assertEquals(CacheResultType.HIT, dirArtifactCache.fetch(
            ruleKeyY,
            LazyPath.ofInstance(fileY)).getType());

    Files.setAttribute(
        dirArtifactCache.getPathForRuleKey(ruleKeyY, Optional.absent()),
        "lastAccessTime",
        FileTime.fromMillis(1000));
    Files.setAttribute(
        dirArtifactCache.getPathForRuleKey(ruleKeyY, Optional.of(".metadata")),
        "lastAccessTime",
        FileTime.fromMillis(1000));

    dirArtifactCache.store(
        ArtifactInfo.builder().addRuleKeys(ruleKeyZ).build(),
        BorrowablePath.notBorrowablePath(fileZ));

    assertEquals(CacheResultType.MISS, dirArtifactCache.fetch(
            ruleKeyX,
            LazyPath.ofInstance(fileX)).getType());
    assertEquals(CacheResultType.MISS, dirArtifactCache.fetch(
            ruleKeyY,
            LazyPath.ofInstance(fileY)).getType());
    assertEquals(CacheResultType.HIT, dirArtifactCache.fetch(
            ruleKeyZ,
            LazyPath.ofInstance(fileZ)).getType());
  }

  @Test
  public void testCacheStoreMultipleKeys() throws IOException {
    Path cacheDir = tmpDir.newFolder();
    Path fileX = tmpDir.newFile("x");

    fileHashCache = new FakeFileHashCache(ImmutableMap.of(fileX, HashCode.fromInt(0)));

    dirArtifactCache = new DirArtifactCache(
        "dir",
        new ProjectFilesystem(cacheDir),
        Paths.get("."),
        /* doStore */ true,
        /* maxCacheSizeBytes */ Optional.absent());

    Files.write(fileX, "x".getBytes(UTF_8));

    RuleKey ruleKey1 = new RuleKey("aaaa");
    RuleKey ruleKey2 = new RuleKey("bbbb");

    dirArtifactCache.store(
        ArtifactInfo.builder().addRuleKeys(ruleKey1, ruleKey2).build(),
        BorrowablePath.notBorrowablePath(fileX));

    // Test that artifact is available via both keys.
    assertEquals(CacheResultType.HIT, dirArtifactCache.fetch(
            ruleKey1,
            LazyPath.ofInstance(fileX)).getType());
    assertEquals(CacheResultType.HIT, dirArtifactCache.fetch(
            ruleKey2,
            LazyPath.ofInstance(fileX)).getType());
  }

  @Test
  public void testCacheStoreAndFetchMetadata() throws IOException {
    FakeProjectFilesystem filesystem = new FakeProjectFilesystem();
    DirArtifactCache cache =
        new DirArtifactCache(
            "dir",
            filesystem,
            Paths.get("cache"),
            /* doStore */ true,
            /* maxCacheSizeBytes */ Optional.absent());

    RuleKey ruleKey = new RuleKey("0000");
    ImmutableMap<String, String> metadata = ImmutableMap.of("some", "metadata");

    // Create a dummy data file.
    Path data = Paths.get("data");
    filesystem.touch(data);

    // Store the artifact with metadata then re-fetch.
    cache.store(
        ArtifactInfo.builder().addRuleKeys(ruleKey).setMetadata(metadata).build(),
        BorrowablePath.notBorrowablePath(data));
    CacheResult result = cache.fetch(ruleKey, LazyPath.ofInstance(Paths.get("out-data")));

    // Verify that the metadata is correct.
    assertThat(
        result.getType(),
        Matchers.equalTo(CacheResultType.HIT));
    assertThat(
        result.getMetadata(),
        Matchers.equalTo(metadata));
    assertThat(
        result.getArtifactSizeBytes(),
        Matchers.equalTo(filesystem.getFileSize(data)));

    cache.close();
  }

  @Test
  public void testFolderLevelsForRuleKeys() throws IOException {
    DirArtifactCache cache = new DirArtifactCache(
        "dir",
        new FakeProjectFilesystem(),
        Paths.get("cache"),
        /* doStore */ false,
        /* maxCacheSizeBytes */ Optional.absent());

    Path result = cache.getPathForRuleKey(
        new RuleKey("aabb0123123234e324"),
        Optional.absent());
    assertThat(result.endsWith(Paths.get("aa/bb/aabb0123123234e324")), Matchers.equalTo(true));

    result = cache.getPathForRuleKey(
        new RuleKey("aabb0123123234e324"),
        Optional.of(".ext"));
    assertThat(result.endsWith(Paths.get("aa/bb/aabb0123123234e324.ext")), Matchers.equalTo(true));

    cache.close();
  }

  private static class BuildRuleForTest extends FakeBuildRule {

    @SuppressWarnings("PMD.UnusedPrivateField")
    @AddToRuleKey
    private final SourcePath file;

    private BuildRuleForTest(Path file) {
      super(
          BuildTargetFactory.newInstance("//foo:" + file.getFileName().toString()),
          new SourcePathResolver(
              new BuildRuleResolver(
                  TargetGraph.EMPTY,
                  new DefaultTargetNodeToBuildRuleTransformer())));
      this.file = new PathSourcePath(new FakeProjectFilesystem(), file);
    }
  }
}
