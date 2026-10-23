package dev.patra.tool.objectstorage.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;

/// S3 提供者的删除：分批删除与桶不存在的处理。
@DisplayName("S3 存储提供者")
class S3StorageProviderTest {

  private static final String BUCKET = "test-bucket";

  @Test
  @DisplayName("按前缀删除 1500 个对象分两批")
  void should_delete_by_prefix_in_batches_of_1000() {
    S3Client client = mock(S3Client.class);
    List<S3Object> objects =
        IntStream.range(0, 1500).mapToObj(i -> S3Object.builder().key("p/" + i).build()).toList();
    when(client.listObjectsV2(any(ListObjectsV2Request.class)))
        .thenReturn(ListObjectsV2Response.builder().contents(objects).isTruncated(false).build());
    when(client.listObjectsV2Paginator(any(ListObjectsV2Request.class)))
        .thenAnswer(
            invocation ->
                new ListObjectsV2Iterable(
                    client, invocation.getArgument(0, ListObjectsV2Request.class)));
    when(client.deleteObjects(any(DeleteObjectsRequest.class)))
        .thenReturn(DeleteObjectsResponse.builder().build());

    int deleted = new S3StorageProvider(client, 1024).deleteByPrefix(BUCKET, "p/");

    assertThat(deleted).isEqualTo(1500);
    ArgumentCaptor<DeleteObjectsRequest> captor =
        ArgumentCaptor.forClass(DeleteObjectsRequest.class);
    verify(client, times(2)).deleteObjects(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(request -> request.delete().objects().size())
        .containsExactly(1000, 500);
  }

  @Test
  @DisplayName("桶不存在时按前缀删除返回 0")
  void should_return_zero_when_bucket_missing() {
    S3Client client = mock(S3Client.class);
    when(client.listObjectsV2Paginator(any(ListObjectsV2Request.class)))
        .thenThrow(NoSuchBucketException.builder().message("no bucket").build());

    assertThat(new S3StorageProvider(client, 1024).deleteByPrefix(BUCKET, "p/")).isZero();
  }

  @Test
  @DisplayName("删除单个对象委托给 deleteObject")
  void should_delete_single_object() {
    S3Client client = mock(S3Client.class);

    new S3StorageProvider(client, 1024).delete(BUCKET, "a/1");

    verify(client).deleteObject(any(DeleteObjectRequest.class));
  }
}
