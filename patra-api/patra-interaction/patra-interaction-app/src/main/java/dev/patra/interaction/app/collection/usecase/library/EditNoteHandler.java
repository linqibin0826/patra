package dev.patra.interaction.app.collection.usecase.library;

import dev.patra.interaction.domain.collection.exception.LibraryItemNotFoundException;
import dev.patra.interaction.domain.collection.model.vo.ItemNote;
import dev.patra.interaction.domain.collection.port.repository.LibraryRepository;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.cqrs.CommandHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 改备注：先按规则解析（超长、非法字符 422），再写；不在库里 404。
@Component
@RequiredArgsConstructor
public class EditNoteHandler implements CommandHandler<EditNoteCommand, Void> {

  private final LibraryRepository library;
  private final CurrentUserPort currentUser;
  private final TransactionOperations transactions;

  /// 改备注。
  ///
  /// @param command 命令
  /// @return `null`
  @Override
  public Void handle(EditNoteCommand command) {
    long userId = currentUser.require().userId();
    String note = ItemNote.parse(command.note()).map(ItemNote::value).orElse(null);
    SubjectId itemId = SubjectId.of(command.itemId());
    Boolean updated = transactions.execute(status -> library.updateNote(userId, itemId, note));
    if (!Boolean.TRUE.equals(updated)) {
      throw new LibraryItemNotFoundException();
    }
    return null;
  }
}
