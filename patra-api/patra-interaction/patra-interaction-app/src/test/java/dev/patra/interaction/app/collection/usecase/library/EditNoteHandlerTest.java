package dev.patra.interaction.app.collection.usecase.library;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.interaction.app.collection.RecordingTransactions;
import dev.patra.interaction.domain.collection.exception.InvalidCollectionFieldsException;
import dev.patra.interaction.domain.collection.exception.LibraryItemNotFoundException;
import dev.patra.interaction.domain.collection.port.repository.LibraryRepository;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.ClientType;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/// 改备注：规范化后写入，空白即删除；不在库里 404。
class EditNoteHandlerTest {

  private static final long U = 1001L;
  private static final SubjectId S1 = SubjectId.of(11L);

  private final LibraryRepository library = mock(LibraryRepository.class);
  private final CurrentUserPort currentUser = mock(CurrentUserPort.class);
  private EditNoteHandler handler;

  @BeforeEach
  void setUp() {
    when(currentUser.require()).thenReturn(CurrentUser.of(U, 1L, AccountType.USER, ClientType.WEB));
    handler =
        new EditNoteHandler(library, currentUser, new RecordingTransactions(new ArrayList<>()));
  }

  /// 去首尾空白后写入。
  @Test
  void should_write_normalized_note() {
    when(library.updateNote(U, S1, "组会讲")).thenReturn(true);

    handler.handle(EditNoteCommand.of(11L, "  组会讲 "));

    verify(library).updateNote(U, S1, "组会讲");
  }

  /// 空白备注 = 删除。
  @Test
  void should_clear_note_when_blank() {
    when(library.updateNote(U, S1, null)).thenReturn(true);

    handler.handle(EditNoteCommand.of(11L, "  "));

    verify(library).updateNote(U, S1, null);
  }

  /// 不在库里。
  @Test
  void should_404_when_item_missing() {
    when(library.updateNote(U, S1, "x")).thenReturn(false);

    assertThatThrownBy(() -> handler.handle(EditNoteCommand.of(11L, "x")))
        .isInstanceOf(LibraryItemNotFoundException.class);
  }

  /// 超长在写库前就拒。
  @Test
  void should_reject_long_note() {
    assertThatThrownBy(() -> handler.handle(EditNoteCommand.of(11L, "字".repeat(501))))
        .isInstanceOf(InvalidCollectionFieldsException.class);
  }
}
