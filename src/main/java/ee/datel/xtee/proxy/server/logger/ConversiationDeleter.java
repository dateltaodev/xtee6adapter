package ee.datel.xtee.proxy.server.logger;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Removed request/response report when age &gt; then "application.logging.max-age" months.
 *
 * @author aldoa
 *
 */
public class ConversiationDeleter {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final Timer timer;

  /**
   * Creates request/response registry old files deleter.
   * 
   * @param logPath path to log files
   * @param months logs months
   */
  public ConversiationDeleter(final Path logPath, final String months) {
    final Path tempdir = logPath;
    final long maxage = Long.parseLong(months);

    timer = new Timer(true);
    timer.schedule(new TimerTask() {
      @Override
      public void run() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        long oldestToKeep = now.minusMonths(maxage).toEpochSecond(ZoneOffset.UTC);
        final FileTime minFileTime = FileTime.from(oldestToKeep, TimeUnit.SECONDS);
        try {
          Files.walkFileTree(tempdir, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
              if (Thread.interrupted()) {
                return FileVisitResult.TERMINATE;
              }
              if (Files.getLastModifiedTime(file).compareTo(minFileTime) < 0) {
                try {
                  Files.delete(file);
                } catch (Exception e) {
                  // DO NOTHING
                }
              }
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(final Path file, final IOException ex) throws IOException {
              return FileVisitResult.SKIP_SUBTREE;
            }

            @Override
            public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) throws IOException {
              if (exc == null) {
                if (Thread.interrupted() || dir.equals(tempdir)) {
                  return FileVisitResult.TERMINATE;
                }
                FileTime created = (FileTime) Files.getAttribute(dir, "creationTime");
                if (created == null) {
                  created = Files.getLastModifiedTime(dir);
                }
                if (created.compareTo(minFileTime) < 0) {
                  try {
                    Files.delete(dir);
                    // } catch (DirectoryNotEmptyException ex) {
                    // DO NOTHING
                  } catch (Exception e) {
                    // DO NOTHING
                  }
                }
              }
              return FileVisitResult.CONTINUE;
            }
          });
        } catch (AccessDeniedException e) {
          // try next time
          return;
        } catch (Exception e) {
          logger.warn("{]\n{}", e.getClass().getName(), e.getMessage());
          // DO NOTHING
        }
      }
    }, 15 * 60 * 1000, 60 * 60 * 1000);
  }

  public void destroy() {
    timer.cancel();
  }
}
