package dev.patra.interaction.adapter.collection.rest;

import dev.patra.interaction.adapter.collection.rest.request.NoteRequest;
import dev.patra.interaction.adapter.collection.rest.request.StatusRequest;
import dev.patra.interaction.adapter.collection.rest.response.CollectResponse;
import dev.patra.interaction.adapter.collection.rest.response.ExportKeysResponse;
import dev.patra.interaction.adapter.collection.rest.response.ItemStatusResponse;
import dev.patra.interaction.adapter.collection.rest.response.LibraryItemResponse;
import dev.patra.interaction.adapter.collection.rest.response.LibraryOverviewResponse;
import dev.patra.interaction.adapter.collection.rest.response.PageResponse;
import dev.patra.interaction.adapter.collection.rest.response.StatusResponse;
import dev.patra.interaction.app.collection.usecase.library.CollectItemCommand;
import dev.patra.interaction.app.collection.usecase.library.CollectResult;
import dev.patra.interaction.app.collection.usecase.library.EditNoteCommand;
import dev.patra.interaction.app.collection.usecase.library.RemoveItemsCommand;
import dev.patra.interaction.app.collection.usecase.query.LibraryQueryService;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
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

/// 当前用户的文献库（都要登录，未登录 401）：概览、条目列表、收藏、移除、备注、批量「我的状态」、导出 key。
@Tag(name = "MyLibrary", description = "我的文献库")
@RestController
@RequestMapping("/me/library")
@RequiredArgsConstructor
public class MyLibraryController {

  private final CommandBus commandBus;
  private final LibraryQueryService libraryQuery;

  /// 概览：「全部」「未分类」两个计数与全部收藏夹（按更新时间倒序）。
  ///
  /// @return 概览
  @GetMapping
  public LibraryOverviewResponse overview() {
    return LibraryOverviewResponse.from(libraryQuery.overview());
  }

  /// 文献库的一页。
  ///
  /// @param view `all`（默认）/ `unfiled`
  /// @param sort `added`（默认）/ `published` / `journal`
  /// @param page 页码，从 1 开始
  /// @param size 每页 1 ~ 100，默认 50
  /// @return 一页
  @GetMapping("/items")
  public PageResponse<LibraryItemResponse> items(
      @RequestParam(required = false) String view,
      @RequestParam(required = false) String sort,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "50") int size) {
    return PageResponse.from(
        libraryQuery.libraryItems(view, sort, page, size), LibraryItemResponse::from);
  }

  /// 收藏（幂等）：已在库里回 `created=false` 与已有条目。404 文献不存在或已下线，503 catalog 不可用，409
  /// `LIBRARY_FULL`。
  ///
  /// @param type 对象类型（`publication`）
  /// @param key catalog 文献 ID
  /// @return 结果与条目
  @PutMapping("/{type}/{key}")
  public CollectResponse collect(@PathVariable String type, @PathVariable String key) {
    CollectResult result = commandBus.handle(CollectItemCommand.of(type, key));
    return CollectResponse.of(
        result.created(), LibraryItemResponse.from(libraryQuery.item(result.itemId())));
  }

  /// 从文献库移除（幂等），同时移出所有收藏夹、删备注。
  ///
  /// @param itemIds 条目 ID，1 ~ 100 个，逗号分隔
  @DeleteMapping("/items")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void remove(@RequestParam(required = false) List<Long> itemIds) {
    commandBus.handle(RemoveItemsCommand.of(itemIds));
  }

  /// 改备注；`null` 或空白为删除。不在库里 404。
  ///
  /// @param itemId 条目 ID
  /// @param request 请求体
  /// @return 改后的条目
  @PutMapping("/items/{itemId}/note")
  public LibraryItemResponse editNote(@PathVariable long itemId, @RequestBody NoteRequest request) {
    commandBus.handle(EditNoteCommand.of(itemId, request.note()));
    return LibraryItemResponse.from(libraryQuery.item(SubjectId.of(itemId)));
  }

  /// 批量「我的状态」：一次 1 ~ 100 个 key，每个 key 一行。
  ///
  /// @param request 请求体
  /// @return 状态
  @PostMapping("/status")
  public StatusResponse status(@RequestBody StatusRequest request) {
    return StatusResponse.of(
        libraryQuery.status(request.type(), request.keys()).stream()
            .map(ItemStatusResponse::from)
            .toList());
  }

  /// 整个文献库或「未分类」可导出的 catalog 文献 ID（≤ 5,000，否则 422 `EXPORT_TOO_LARGE`）。
  ///
  /// @param view `all`（默认）/ `unfiled`
  /// @param sort 排序，决定 key 的顺序
  /// @return key
  @GetMapping("/keys")
  public ExportKeysResponse keys(
      @RequestParam(required = false) String view, @RequestParam(required = false) String sort) {
    return ExportKeysResponse.from(libraryQuery.libraryKeys(view, sort));
  }
}
