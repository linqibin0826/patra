package dev.patra.tool.httpclient.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.tool.httpclient.download.DownloadClient;
import dev.patra.tool.httpclient.download.strategy.FtpStreamingDownloader;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class HttpClientDownloadAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  HttpClientStreamingAutoConfiguration.class,
                  HttpClientDownloadAutoConfiguration.class));

  @Test
  void should_register_download_client_and_ftp_downloader_by_default() {
    runner.run(
        ctx ->
            assertThat(ctx)
                .hasSingleBean(DownloadClient.class)
                .hasSingleBean(FtpStreamingDownloader.class));
  }

  @Test
  void should_skip_ftp_downloader_when_switched_off() {
    runner
        .withPropertyValues("patra.http-client.download.ftp.enabled=false")
        .run(
            ctx ->
                assertThat(ctx)
                    .hasSingleBean(DownloadClient.class)
                    .doesNotHaveBean(FtpStreamingDownloader.class));
  }

  @Test
  void should_register_nothing_when_download_disabled() {
    runner
        .withPropertyValues("patra.http-client.download.enabled=false")
        .run(ctx -> assertThat(ctx).doesNotHaveBean(DownloadClient.class));
  }
}
