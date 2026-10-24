package dev.patra.interaction.adapter.collection.rest;

import dev.patra.interaction.adapter.collection.rest.response.CopyResultResponse;
import dev.patra.interaction.adapter.collection.rest.response.ExportKeysResponse;
import dev.patra.interaction.adapter.collection.rest.response.PageResponse;
import dev.patra.interaction.adapter.collection.rest.response.PublicCollectionCardResponse;
import dev.patra.interaction.adapter.collection.rest.response.PublicCollectionResponse;
import dev.patra.interaction.adapter.collection.rest.response.PublicItemResponse;
import dev.patra.interaction.adapter.collection.rest.response.UserCollectionsResponse;
import dev.patra.interaction.app.collection.usecase.copy.CopyCollectionCommand;
import dev.patra.interaction.app.collection.usecase.query.PublicCollectionQueryService;
import dev.patra.tool.cqrs.CommandBus;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/// 别人能看的收藏夹（登录可选；访客已登录时带会话）。所有者本人能读自己的私有夹；别人只能读公开的、且 identity 判定
/// 所有者主页对他可见的，否则一律 404；identity 不可用 503。
@Tag(name = "PublicCollections", description = "公开收藏夹")
@RestController
@RequiredArgsConstructor
public class PublicCollectionController {

  private final CommandBus commandBus;
  private final PublicCollectionQueryService publicQuery;

  /// 收藏夹头部与所有者摘要。
  ///
  /// @param id 收藏夹
  /// @return 头部
  @GetMapping("/collections/{id}")
  public PublicCollectionResponse collection(@PathVariable long id) {
    return PublicCollectionResponse.from(publicQuery.collection(id));
  }

  /// 条目的一页（不带备注）。
  ///
  /// @param id 收藏夹
  /// @param sort `added`（默认）/ `published` / `journal`
  /// @param page 页码，从 1 开始
  /// @param size 每页 1 ~ 100，默认 50
  /// @return 一页
  @GetMapping("/collections/{id}/items")
  public PageResponse<PublicItemResponse> items(
      @PathVariable long id,
      @RequestParam(required = false) String sort,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "50") int size) {
    return PageResponse.from(publicQuery.items(id, sort, page, size), PublicItemResponse::from);
  }

  /// 可导出的 catalog 文献 ID（访客也能导出、复制引用对他可见的公开收藏夹）。
  ///
  /// @param id 收藏夹
  /// @param sort 排序，决定 key 的顺序
  /// @return key
  @GetMapping("/collections/{id}/keys")
  public ExportKeysResponse keys(
      @PathVariable long id, @RequestParam(required = false) String sort) {
    return ExportKeysResponse.from(publicQuery.keys(id, sort));
  }

  /// 复制成自己的私有收藏夹（要登录）。409 `COLLECTION_LIMIT_REACHED` / `LIBRARY_FULL`（整体失败）。
  ///
  /// @param id 源收藏夹
  /// @return 结果
  @PostMapping("/collections/{id}/copies")
  @ResponseStatus(HttpStatus.CREATED)
  public CopyResultResponse copy(@PathVariable long id) {
    return CopyResultResponse.from(commandBus.handle(CopyCollectionCommand.of(id)));
  }

  /// 主页「收藏夹」栏：这个用户对访客可见的公开收藏夹；不可见回空列表。
  ///
  /// @param userId 主页的主人
  /// @return 卡片
  @GetMapping("/users/{userId}/collections")
  public UserCollectionsResponse userCollections(@PathVariable long userId) {
    return UserCollectionsResponse.of(
        publicQuery.userCollections(userId).stream()
            .map(PublicCollectionCardResponse::from)
            .toList());
  }
}
