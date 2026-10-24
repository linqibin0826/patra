package dev.patra.interaction.adapter.collection.rest;

import dev.patra.interaction.adapter.collection.rest.request.CollectionTextRequest;
import dev.patra.interaction.adapter.collection.rest.request.ItemIdsRequest;
import dev.patra.interaction.adapter.collection.rest.request.VisibilityRequest;
import dev.patra.interaction.adapter.collection.rest.response.AddToCollectionResponse;
import dev.patra.interaction.adapter.collection.rest.response.CollectionResponse;
import dev.patra.interaction.adapter.collection.rest.response.ExportKeysResponse;
import dev.patra.interaction.adapter.collection.rest.response.LibraryItemResponse;
import dev.patra.interaction.adapter.collection.rest.response.PageResponse;
import dev.patra.interaction.app.collection.usecase.collection.AddToCollectionCommand;
import dev.patra.interaction.app.collection.usecase.collection.CreateCollectionCommand;
import dev.patra.interaction.app.collection.usecase.collection.DeleteCollectionCommand;
import dev.patra.interaction.app.collection.usecase.collection.RemoveFromCollectionCommand;
import dev.patra.interaction.app.collection.usecase.collection.SetVisibilityCommand;
import dev.patra.interaction.app.collection.usecase.collection.UpdateCollectionCommand;
import dev.patra.interaction.app.collection.usecase.query.LibraryQueryService;
import dev.patra.tool.cqrs.CommandBus;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/// 当前用户自己的收藏夹（都要登录）。不存在或不是自己的收藏夹一律 404（删除除外：幂等 204）。
@Tag(name = "MyCollections", description = "我的收藏夹")
@RestController
@RequestMapping("/me/collections")
@RequiredArgsConstructor
public class MyCollectionController {

  private final CommandBus commandBus;
  private final LibraryQueryService libraryQuery;

  /// 新建私有收藏夹。422 名称 / 简介不合法，409 `COLLECTION_NAME_TAKEN` / `COLLECTION_LIMIT_REACHED`。
  ///
  /// @param request 请求体
  /// @return 新收藏夹
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CollectionResponse create(@RequestBody CollectionTextRequest request) {
    return CollectionResponse.from(
        commandBus.handle(CreateCollectionCommand.of(request.name(), request.description())));
  }

  /// 改名称与简介（整体替换）。
  ///
  /// @param id 收藏夹
  /// @param request 请求体
  /// @return 改后的收藏夹
  @PutMapping("/{id}")
  public CollectionResponse update(
      @PathVariable long id, @RequestBody CollectionTextRequest request) {
    return CollectionResponse.from(
        commandBus.handle(UpdateCollectionCommand.of(id, request.name(), request.description())));
  }

  /// 设可见性。设为公开时回查 identity：409 `ACCOUNT_RESTRICTED` / `EMAIL_UNVERIFIED`，503 identity 不可用。
  ///
  /// @param id 收藏夹
  /// @param request 请求体
  /// @return 改后的收藏夹
  @PutMapping("/{id}/visibility")
  public CollectionResponse setVisibility(
      @PathVariable long id, @RequestBody VisibilityRequest request) {
    return CollectionResponse.from(
        commandBus.handle(SetVisibilityCommand.of(id, request.visibility())));
  }

  /// 删收藏夹（幂等），里面的条目留在文献库。
  ///
  /// @param id 收藏夹
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable long id) {
    commandBus.handle(DeleteCollectionCommand.of(id));
  }

  /// 收藏夹的一页（条目带加入该夹的时间）。
  ///
  /// @param id 收藏夹
  /// @param sort `added`（默认，按加入该夹的时间）/ `published` / `journal`
  /// @param page 页码，从 1 开始
  /// @param size 每页 1 ~ 100，默认 50
  /// @return 一页
  @GetMapping("/{id}/items")
  public PageResponse<LibraryItemResponse> items(
      @PathVariable long id,
      @RequestParam(required = false) String sort,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "50") int size) {
    return PageResponse.from(
        libraryQuery.collectionItems(id, sort, page, size), LibraryItemResponse::from);
  }

  /// 放进收藏夹：夹满不报错，篇数分类回在结果里。
  ///
  /// @param id 收藏夹
  /// @param request 请求体
  /// @return 结果
  @PostMapping("/{id}/items")
  public AddToCollectionResponse add(@PathVariable long id, @RequestBody ItemIdsRequest request) {
    return AddToCollectionResponse.from(
        commandBus.handle(AddToCollectionCommand.of(id, request.itemIds())));
  }

  /// 移出收藏夹（不在夹里的忽略）。
  ///
  /// @param id 收藏夹
  /// @param itemIds 条目 ID，1 ~ 100 个，逗号分隔
  @DeleteMapping("/{id}/items")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void remove(@PathVariable long id, @RequestParam(required = false) List<Long> itemIds) {
    commandBus.handle(RemoveFromCollectionCommand.of(id, itemIds));
  }

  /// 自己的收藏夹可导出的 catalog 文献 ID。
  ///
  /// @param id 收藏夹
  /// @param sort 排序，决定 key 的顺序
  /// @return key
  @GetMapping("/{id}/keys")
  public ExportKeysResponse keys(
      @PathVariable long id, @RequestParam(required = false) String sort) {
    return ExportKeysResponse.from(libraryQuery.collectionKeys(id, sort));
  }
}
