/**
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

package org.apache.hadoop.yarn.util;

import static org.apache.hadoop.fs.CreateFlag.CREATE;
import static org.apache.hadoop.fs.CreateFlag.OVERWRITE;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import junit.framework.Assert;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.CommonConfigurationKeys;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileContext;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocalDirAllocator;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.yarn.api.records.LocalResource;
import org.apache.hadoop.yarn.api.records.LocalResourceType;
import org.apache.hadoop.yarn.api.records.LocalResourceVisibility;
import org.apache.hadoop.yarn.factories.RecordFactory;
import org.apache.hadoop.yarn.factory.providers.RecordFactoryProvider;
import org.apache.hadoop.yarn.util.ConverterUtils;
import org.junit.AfterClass;
import org.junit.Test;

public class TestFSDownload {

  private static final Log LOG = LogFactory.getLog(TestFSDownload.class);
  private static AtomicLong uniqueNumberGenerator =
    new AtomicLong(System.currentTimeMillis());
  
  @AfterClass
  public static void deleteTestDir() throws IOException {
    FileContext fs = FileContext.getLocalFSFileContext();
    fs.delete(new Path("target", TestFSDownload.class.getSimpleName()), true);
  }

  static final RecordFactory recordFactory =
    RecordFactoryProvider.getRecordFactory(null);

  static LocalResource createFile(FileContext files, Path p, int len,
      Random r, LocalResourceVisibility vis) throws IOException {
    FSDataOutputStream out = null;
    try {
      byte[] bytes = new byte[len];
      out = files.create(p, EnumSet.of(CREATE, OVERWRITE));
      r.nextBytes(bytes);
      out.write(bytes);
    } finally {
      if (out != null) out.close();
    }
    LocalResource ret = recordFactory.newRecordInstance(LocalResource.class);
    ret.setResource(ConverterUtils.getYarnUrlFromPath(p));
    ret.setSize(len);
    ret.setType(LocalResourceType.FILE);
    ret.setVisibility(vis);
    ret.setTimestamp(files.getFileStatus(p).getModificationTime());
    return ret;
  }

  static LocalResource createJar(FileContext files, Path p,
      LocalResourceVisibility vis) throws IOException {
    LOG.info("Create jar file " + p);
    File jarFile = new File((files.makeQualified(p)).toUri());
    FileOutputStream stream = new FileOutputStream(jarFile);
    LOG.info("Create jar out stream ");
    JarOutputStream out = new JarOutputStream(stream, new Manifest());
    LOG.info("Done writing jar stream ");
    out.close();
    LocalResource ret = recordFactory.newRecordInstance(LocalResource.class);
    ret.setResource(ConverterUtils.getYarnUrlFromPath(p));
    FileStatus status = files.getFileStatus(p);
    ret.setSize(status.getLen());
    ret.setTimestamp(status.getModificationTime());
    ret.setType(LocalResourceType.PATTERN);
    ret.setVisibility(vis);
    ret.setPattern("classes/.*");
    return ret;
  }
  
  static LocalResource createTarFile(FileContext files, Path p, int len,
      Random r, LocalResourceVisibility vis) throws IOException,
      URISyntaxException {
    byte[] bytes = new byte[len];
    r.nextBytes(bytes);

    File archiveFile = new File(p.toUri().getPath() + ".tar");
    archiveFile.createNewFile();
    TarArchiveOutputStream out = new TarArchiveOutputStream(
        new FileOutputStream(archiveFile));
    TarArchiveEntry entry = new TarArchiveEntry(p.getName());
    entry.setSize(bytes.length);
    out.putArchiveEntry(entry);
    out.write(bytes);
    out.closeArchiveEntry();
    out.close();

    LocalResource ret = recordFactory.newRecordInstance(LocalResource.class);
    ret.setResource(ConverterUtils.getYarnUrlFromPath(new Path(p.toString()
        + ".tar")));
    ret.setSize(len);
    ret.setType(LocalResourceType.ARCHIVE);
    ret.setVisibility(vis);
    ret.setTimestamp(files.getFileStatus(new Path(p.toString() + ".tar"))
        .getModificationTime());
    return ret;
  }
  
  static LocalResource createJarFile(FileContext files, Path p, int len,
      Random r, LocalResourceVisibility vis) throws IOException,
      URISyntaxException {
    byte[] bytes = new byte[len];
    r.nextBytes(bytes);

    File archiveFile = new File(p.toUri().getPath() + ".jar");
    archiveFile.createNewFile();
    JarOutputStream out = new JarOutputStream(
        new FileOutputStream(archiveFile));
    out.putNextEntry(new JarEntry(p.getName()));
    out.write(bytes);
    out.closeEntry();
    out.close();

    LocalResource ret = recordFactory.newRecordInstance(LocalResource.class);
    ret.setResource(ConverterUtils.getYarnUrlFromPath(new Path(p.toString()
        + ".jar")));
    ret.setSize(len);
    ret.setType(LocalResourceType.ARCHIVE);
    ret.setVisibility(vis);
    ret.setTimestamp(files.getFileStatus(new Path(p.toString() + ".jar"))
        .getModificationTime());
    return ret;
  }
  
  static LocalResource createZipFile(FileContext files, Path p, int len,
      Random r, LocalResourceVisibility vis) throws IOException,
      URISyntaxException {
    byte[] bytes = new byte[len];
    r.nextBytes(bytes);

    File archiveFile = new File(p.toUri().getPath() + ".zip");
    archiveFile.createNewFile();
    ZipOutputStream out = new ZipOutputStream(
        new FileOutputStream(archiveFile));
    out.putNextEntry(new ZipEntry(p.getName()));
    out.write(bytes);
    out.closeEntry();
    out.close();

    LocalResource ret = recordFactory.newRecordInstance(LocalResource.class);
    ret.setResource(ConverterUtils.getYarnUrlFromPath(new Path(p.toString()
        + ".zip")));
    ret.setSize(len);
    ret.setType(LocalResourceType.ARCHIVE);
    ret.setVisibility(vis);
    ret.setTimestamp(files.getFileStatus(new Path(p.toString() + ".zip"))
        .getModificationTime());
    return ret;
  }
  
  @Test (timeout=10000)
  public void testDownloadBadPublic() throws IOException, URISyntaxException,
      InterruptedException {
    Configuration conf = new Configuration();
    conf.set(CommonConfigurationKeys.FS_PERMISSIONS_UMASK_KEY, "077");
    FileContext files = FileContext.getLocalFSFileContext(conf);
    final Path basedir = files.makeQualified(new Path("target",
      TestFSDownload.class.getSimpleName()));
    files.mkdir(basedir, null, true);
    conf.setStrings(TestFSDownload.class.getName(), basedir.toString());
    
    Map<LocalResource, LocalResourceVisibility> rsrcVis =
        new HashMap<LocalResource, LocalResourceVisibility>();

    Random rand = new Random();
    long sharedSeed = rand.nextLong();
    rand.setSeed(sharedSeed);
    System.out.println("SEED: " + sharedSeed);

    Map<LocalResource,Future<Path>> pending =
      new HashMap<LocalResource,Future<Path>>();
    ExecutorService exec = Executors.newSingleThreadExecutor();
    LocalDirAllocator dirs =
      new LocalDirAllocator(TestFSDownload.class.getName());
    int size = 512;
    LocalResourceVisibility vis = LocalResourceVisibility.PUBLIC;
    Path path = new Path(basedir, "test-file");
    LocalResource rsrc = createFile(files, path, size, rand, vis);
    rsrcVis.put(rsrc, vis);
    Path destPath = dirs.getLocalPathForWrite(
        basedir.toString(), size, conf);
    destPath = new Path (destPath,
      Long.toString(uniqueNumberGenerator.incrementAndGet()));
    FSDownload fsd =
      new FSDownload(files, UserGroupInformation.getCurrentUser(), conf,
          destPath, rsrc);
    pending.put(rsrc, exec.submit(fsd));

    try {
      for (Map.Entry<LocalResource,Future<Path>> p : pending.entrySet()) {
        p.getValue().get();
        Assert.fail("We localized a file that is not public.");
      }
    } catch (ExecutionException e) {
      Assert.assertTrue(e.getCause() instanceof IOException);
    } finally {
      exec.shutdown();
    }
  }
  
  @Test (timeout=10000)
  public void testDownload() throws IOException, URISyntaxException,
      InterruptedException {
    Configuration conf = new Configuration();
    conf.set(CommonConfigurationKeys.FS_PERMISSIONS_UMASK_KEY, "077");
    FileContext files = FileContext.getLocalFSFileContext(conf);
    final Path basedir = files.makeQualified(new Path("target",
      TestFSDownload.class.getSimpleName()));
    files.mkdir(basedir, null, true);
    conf.setStrings(TestFSDownload.class.getName(), basedir.toString());
    
    Map<LocalResource, LocalResourceVisibility> rsrcVis =
        new HashMap<LocalResource, LocalResourceVisibility>();

    Random rand = new Random();
    long sharedSeed = rand.nextLong();
    rand.setSeed(sharedSeed);
    System.out.println("SEED: " + sharedSeed);

    Map<LocalResource,Future<Path>> pending =
      new HashMap<LocalResource,Future<Path>>();
    ExecutorService exec = Executors.newSingleThreadExecutor();
    LocalDirAllocator dirs =
      new LocalDirAllocator(TestFSDownload.class.getName());
    int[] sizes = new int[10];
    for (int i = 0; i < 10; ++i) {
      sizes[i] = rand.nextInt(512) + 512;
      LocalResourceVisibility vis = LocalResourceVisibility.PRIVATE;
      if (i%2 == 1) {
        vis = LocalResourceVisibility.APPLICATION;
      }
      Path p = new Path(basedir, "" + i);
      LocalResource rsrc = createFile(files, p, sizes[i], rand, vis);
      rsrcVis.put(rsrc, vis);
      Path destPath = dirs.getLocalPathForWrite(
          basedir.toString(), sizes[i], conf);
      destPath = new Path (destPath,
          Long.toString(uniqueNumberGenerator.incrementAndGet()));
      FSDownload fsd =
          new FSDownload(files, UserGroupInformation.getCurrentUser(), conf,
              destPath, rsrc);
      pending.put(rsrc, exec.submit(fsd));
    }

    try {
      for (Map.Entry<LocalResource,Future<Path>> p : pending.entrySet()) {
        Path localized = p.getValue().get();
        assertEquals(sizes[Integer.valueOf(localized.getName())], p.getKey()
            .getSize());

        FileStatus status = files.getFileStatus(localized.getParent());
        FsPermission perm = status.getPermission();
        assertEquals("Cache directory permissions are incorrect",
            new FsPermission((short)0755), perm);

        status = files.getFileStatus(localized);
        perm = status.getPermission();
        System.out.println("File permission " + perm + 
            " for rsrc vis " + p.getKey().getVisibility().name());
        assert(rsrcVis.containsKey(p.getKey()));
        Assert.assertTrue("Private file should be 500",
            perm.toShort() == FSDownload.PRIVATE_FILE_PERMS.toShort());
      }
    } catch (ExecutionException e) {
      throw new IOException("Failed exec", e);
    } finally {
      exec.shutdown();
    }
  }
  
  @SuppressWarnings("deprecation")
  @Test (timeout=10000) 
  public void testDownloadArchive() throws IOException, URISyntaxException,
      InterruptedException {
    Configuration conf = new Configuration();
    conf.set(CommonConfigurationKeys.FS_PERMISSIONS_UMASK_KEY, "077");
    FileContext files = FileContext.getLocalFSFileContext(conf);
    final Path basedir = files.makeQualified(new Path("target",
        TestFSDownload.class.getSimpleName()));
    files.mkdir(basedir, null, true);
    conf.setStrings(TestFSDownload.class.getName(), basedir.toString());

    Random rand = new Random();
    long sharedSeed = rand.nextLong();
    rand.setSeed(sharedSeed);
    System.out.println("SEED: " + sharedSeed);

    Map<LocalResource, Future<Path>> pending = new HashMap<LocalResource, Future<Path>>();
    ExecutorService exec = Executors.newSingleThreadExecutor();
    LocalDirAllocator dirs = new LocalDirAllocator(
        TestFSDownload.class.getName());

    int size = rand.nextInt(512) + 512;
    LocalResourceVisibility vis = LocalResourceVisibility.PRIVATE;

    Path p = new Path(basedir, "" + 1);
    LocalResource rsrc = createTarFile(files, p, size, rand, vis);
    Path destPath = dirs.getLocalPathForWrite(basedir.toString(), size, conf);
    destPath = new Path (destPath,
        Long.toString(uniqueNumberGenerator.incrementAndGet()));
    FSDownload fsd = new FSDownload(files,
        UserGroupInformation.getCurrentUser(), conf, destPath, rsrc);
    pending.put(rsrc, exec.submit(fsd));
    
    try {
      FileStatus[] filesstatus = files.getDefaultFileSystem().listStatus(
          basedir);
      for (FileStatus filestatus : filesstatus) {
        if (filestatus.isDir()) {
          FileStatus[] childFiles = files.getDefaultFileSystem().listStatus(
              filestatus.getPath());
          for (FileStatus childfile : childFiles) {
            if (childfile.getPath().getName().equalsIgnoreCase("1.tar.tmp")) {
              Assert.fail("Tmp File should not have been there "
                  + childfile.getPath());
            }
          }
        }
      }
    }catch (Exception e) {
      throw new IOException("Failed exec", e);
    }
    finally {
      exec.shutdown();
    }
  }
  
  @SuppressWarnings("deprecation")
  @Test (timeout=10000) 
  public void testDownloadPatternJar() throws IOException, URISyntaxException,
      InterruptedException {
    Configuration conf = new Configuration();
    conf.set(CommonConfigurationKeys.FS_PERMISSIONS_UMASK_KEY, "077");
    FileContext files = FileContext.getLocalFSFileContext(conf);
    final Path basedir = files.makeQualified(new Path("target",
        TestFSDownload.class.getSimpleName()));
    files.mkdir(basedir, null, true);
    conf.setStrings(TestFSDownload.class.getName(), basedir.toString());

    Random rand = new Random();
    long sharedSeed = rand.nextLong();
    rand.setSeed(sharedSeed);
    System.out.println("SEED: " + sharedSeed);

    Map<LocalResource, Future<Path>> pending = new HashMap<LocalResource, Future<Path>>();
    ExecutorService exec = Executors.newSingleThreadExecutor();
    LocalDirAllocator dirs = new LocalDirAllocator(
        TestFSDownload.class.getName());

    int size = rand.nextInt(512) + 512;
    LocalResourceVisibility vis = LocalResourceVisibility.PRIVATE;

    Path p = new Path(basedir, "" + 1);
    LocalResource rsrcjar = createJarFile(files, p, size, rand, vis);
    rsrcjar.setType(LocalResourceType.PATTERN);
    Path destPathjar = dirs.getLocalPathForWrite(basedir.toString(), size, conf);
    destPathjar = new Path (destPathjar,
        Long.toString(uniqueNumberGenerator.incrementAndGet()));
    FSDownload fsdjar = new FSDownload(files,
        UserGroupInformation.getCurrentUser(), conf, destPathjar, rsrcjar);
    pending.put(rsrcjar, exec.submit(fsdjar));

    try {
      FileStatus[] filesstatus = files.getDefaultFileSystem().listStatus(
          basedir);
      for (FileStatus filestatus : filesstatus) {
        if (filestatus.isDir()) {
          FileStatus[] childFiles = files.getDefaultFileSystem().listStatus(
              filestatus.getPath());
          for (FileStatus childfile : childFiles) {
            if (childfile.getPath().getName().equalsIgnoreCase("1.jar.tmp")) {
              Assert.fail("Tmp File should not have been there "
                  + childfile.getPath());
            }
          }
        }
      }
    }catch (Exception e) {
      throw new IOException("Failed exec", e);
    }
    finally {
      exec.shutdown();
    }
  }
  
  @SuppressWarnings("deprecation")
  @Test (timeout=10000) 
  public void testDownloadArchiveZip() throws IOException, URISyntaxException,
      InterruptedException {
    Configuration conf = new Configuration();
    conf.set(CommonConfigurationKeys.FS_PERMISSIONS_UMASK_KEY, "077");
    FileContext files = FileContext.getLocalFSFileContext(conf);
    final Path basedir = files.makeQualified(new Path("target",
        TestFSDownload.class.getSimpleName()));
    files.mkdir(basedir, null, true);
    conf.setStrings(TestFSDownload.class.getName(), basedir.toString());

    Random rand = new Random();
    long sharedSeed = rand.nextLong();
    rand.setSeed(sharedSeed);
    System.out.println("SEED: " + sharedSeed);

    Map<LocalResource, Future<Path>> pending = new HashMap<LocalResource, Future<Path>>();
    ExecutorService exec = Executors.newSingleThreadExecutor();
    LocalDirAllocator dirs = new LocalDirAllocator(
        TestFSDownload.class.getName());

    int size = rand.nextInt(512) + 512;
    LocalResourceVisibility vis = LocalResourceVisibility.PRIVATE;

    Path p = new Path(basedir, "" + 1);
    LocalResource rsrczip = createZipFile(files, p, size, rand, vis);
    Path destPathjar = dirs.getLocalPathForWrite(basedir.toString(), size, conf);
    destPathjar = new Path (destPathjar,
        Long.toString(uniqueNumberGenerator.incrementAndGet()));
    FSDownload fsdzip = new FSDownload(files,
        UserGroupInformation.getCurrentUser(), conf, destPathjar, rsrczip);
    pending.put(rsrczip, exec.submit(fsdzip));

    try {
      FileStatus[] filesstatus = files.getDefaultFileSystem().listStatus(
          basedir);
      for (FileStatus filestatus : filesstatus) {
        if (filestatus.isDir()) {
          FileStatus[] childFiles = files.getDefaultFileSystem().listStatus(
              filestatus.getPath());
          for (FileStatus childfile : childFiles) {
            if (childfile.getPath().getName().equalsIgnoreCase("1.gz.tmp")) {
              Assert.fail("Tmp File should not have been there "
                  + childfile.getPath());
            }
          }
        }
      }
    }catch (Exception e) {
      throw new IOException("Failed exec", e);
    }
    finally {
      exec.shutdown();
    }
  }
  
  private void verifyPermsRecursively(FileSystem fs,
      FileContext files, Path p,
      LocalResourceVisibility vis) throws IOException {
    FileStatus status = files.getFileStatus(p);
    if (status.isDirectory()) {
      if (vis == LocalResourceVisibility.PUBLIC) {
        Assert.assertTrue(status.getPermission().toShort() ==
          FSDownload.PUBLIC_DIR_PERMS.toShort());
      }
      else {
        Assert.assertTrue(status.getPermission().toShort() ==
          FSDownload.PRIVATE_DIR_PERMS.toShort());
      }
      if (!status.isSymlink()) {
        FileStatus[] statuses = fs.listStatus(p);
        for (FileStatus stat : statuses) {
          verifyPermsRecursively(fs, files, stat.getPath(), vis);
        }
      }
    }
    else {
      if (vis == LocalResourceVisibility.PUBLIC) {
        Assert.assertTrue(status.getPermission().toShort() ==
          FSDownload.PUBLIC_FILE_PERMS.toShort());
      }
      else {
        Assert.assertTrue(status.getPermission().toShort() ==
          FSDownload.PRIVATE_FILE_PERMS.toShort());
      }
    }      
  }
  
  @Test (timeout=10000)
  public void testDirDownload() throws IOException, InterruptedException {
    Configuration conf = new Configuration();
    FileContext files = FileContext.getLocalFSFileContext(conf);
    final Path basedir = files.makeQualified(new Path("target",
      TestFSDownload.class.getSimpleName()));
    files.mkdir(basedir, null, true);
    conf.setStrings(TestFSDownload.class.getName(), basedir.toString());
    
    Map<LocalResource, LocalResourceVisibility> rsrcVis =
        new HashMap<LocalResource, LocalResourceVisibility>();
  
    Random rand = new Random();
    long sharedSeed = rand.nextLong();
    rand.setSeed(sharedSeed);
    System.out.println("SEED: " + sharedSeed);

    Map<LocalResource,Future<Path>> pending =
      new HashMap<LocalResource,Future<Path>>();
    ExecutorService exec = Executors.newSingleThreadExecutor();
    LocalDirAllocator dirs =
      new LocalDirAllocator(TestFSDownload.class.getName());
    for (int i = 0; i < 5; ++i) {
      LocalResourceVisibility vis = LocalResourceVisibility.PRIVATE;
      if (i%2 == 1) {
        vis = LocalResourceVisibility.APPLICATION;
      }

      Path p = new Path(basedir, "dir" + i + ".jar");
      LocalResource rsrc = createJar(files, p, vis);
      rsrcVis.put(rsrc, vis);
      Path destPath = dirs.getLocalPathForWrite(
          basedir.toString(), conf);
      destPath = new Path (destPath,
          Long.toString(uniqueNumberGenerator.incrementAndGet()));
      FSDownload fsd =
          new FSDownload(files, UserGroupInformation.getCurrentUser(), conf,
              destPath, rsrc);
      pending.put(rsrc, exec.submit(fsd));
    }
    
    try {
      
      for (Map.Entry<LocalResource,Future<Path>> p : pending.entrySet()) {
        Path localized = p.getValue().get();
        FileStatus status = files.getFileStatus(localized);

        System.out.println("Testing path " + localized);
        assert(status.isDirectory());
        assert(rsrcVis.containsKey(p.getKey()));
        
        verifyPermsRecursively(localized.getFileSystem(conf),
            files, localized, rsrcVis.get(p.getKey()));
      }
    } catch (ExecutionException e) {
      throw new IOException("Failed exec", e);
    } finally {
      exec.shutdown();
    }
    
    

  }

  @Test(timeout = 1000)
  public void testUniqueDestinationPath() throws Exception {
    Configuration conf = new Configuration();
    FileContext files = FileContext.getLocalFSFileContext(conf);
    final Path basedir = files.makeQualified(new Path("target",
        TestFSDownload.class.getSimpleName()));
    files.mkdir(basedir, null, true);
    conf.setStrings(TestFSDownload.class.getName(), basedir.toString());

    ExecutorService singleThreadedExec = Executors.newSingleThreadExecutor();

    LocalDirAllocator dirs =
        new LocalDirAllocator(TestFSDownload.class.getName());
    Path destPath = dirs.getLocalPathForWrite(basedir.toString(), conf);
    destPath =
        new Path(destPath, Long.toString(uniqueNumberGenerator
            .incrementAndGet()));
    try {
      Path p = new Path(basedir, "dir" + 0 + ".jar");
      LocalResourceVisibility vis = LocalResourceVisibility.PRIVATE;
      LocalResource rsrc = createJar(files, p, vis);
      FSDownload fsd =
          new FSDownload(files, UserGroupInformation.getCurrentUser(), conf,
              destPath, rsrc);
      Future<Path> rPath = singleThreadedExec.submit(fsd);
      // Now FSDownload will not create a random directory to localize the
      // resource. Therefore the final localizedPath for the resource should be
      // destination directory (passed as an argument) + file name.
      Assert.assertEquals(destPath, rPath.get().getParent());
    } finally {
      singleThreadedExec.shutdown();
    }
  }
}