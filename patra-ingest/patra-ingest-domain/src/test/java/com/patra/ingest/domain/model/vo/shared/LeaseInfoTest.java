package com.patra.ingest.domain.model.vo.shared;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link LeaseInfo} 单元测试。
 *
 * <p>测试策略: Record 值对象 - 纯单元测试,无 Mock。
 *
 * <p>测试覆盖:
 *
 * <ul>
 *   <li>✅ Record 语义 (equals, hashCode, toString)
 *   <li>✅ 不变性约束 (leaseCount >= 0)
 *   <li>✅ 工厂方法 (none, snapshotOf)
 *   <li>✅ 业务方法 (isHeld, acquire, renew, release)
 *   <li>✅ 边界条件和异常场景
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@DisplayName("LeaseInfo 值对象单元测试")
class LeaseInfoTest {

  @Nested
  @DisplayName("构造函数和不变性约束")
  class ConstructorAndInvariantsTests {

    @Test
    @DisplayName("应该成功创建有效的租约信息")
    void shouldCreateValidLeaseInfo() {
      // Given
      String owner = "node-1";
      Instant until = Instant.parse("2025-01-01T00:00:00Z");
      int leaseCount = 5;

      // When
      LeaseInfo leaseInfo = new LeaseInfo(owner, until, leaseCount);

      // Then
      assertThat(leaseInfo.owner()).isEqualTo(owner);
      assertThat(leaseInfo.leasedUntil()).isEqualTo(until);
      assertThat(leaseInfo.leaseCount()).isEqualTo(leaseCount);
    }

    @Test
    @DisplayName("应该允许创建租约计数为零的租约信息")
    void shouldAllowZeroLeaseCount() {
      // Given & When
      LeaseInfo leaseInfo = new LeaseInfo("owner", Instant.now(), 0);

      // Then
      assertThat(leaseInfo.leaseCount()).isZero();
    }

    @Test
    @DisplayName("应该拒绝负数租约计数")
    void shouldRejectNegativeLeaseCount() {
      // Given
      String owner = "node-1";
      Instant until = Instant.now();
      int negativeCount = -1;

      // When & Then
      assertThatThrownBy(() -> new LeaseInfo(owner, until, negativeCount))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("leaseCount must not be negative");
    }

    @Test
    @DisplayName("应该允许空 owner 和 leasedUntil")
    void shouldAllowNullOwnerAndLeasedUntil() {
      // Given & When
      LeaseInfo leaseInfo = new LeaseInfo(null, null, 0);

      // Then
      assertThat(leaseInfo.owner()).isNull();
      assertThat(leaseInfo.leasedUntil()).isNull();
    }
  }

  @Nested
  @DisplayName("Record 语义测试")
  class RecordSemanticsTests {

    @Test
    @DisplayName("相同属性的租约信息应该相等")
    void shouldBeEqualWithSameProperties() {
      // Given
      String owner = "node-1";
      Instant until = Instant.parse("2025-01-01T00:00:00Z");
      int leaseCount = 3;

      // When
      LeaseInfo leaseInfo1 = new LeaseInfo(owner, until, leaseCount);
      LeaseInfo leaseInfo2 = new LeaseInfo(owner, until, leaseCount);

      // Then
      assertThat(leaseInfo1)
          .isEqualTo(leaseInfo2)
          .hasSameHashCodeAs(leaseInfo2)
          .isNotSameAs(leaseInfo2);
    }

    @Test
    @DisplayName("不同属性的租约信息应该不相等")
    void shouldNotBeEqualWithDifferentProperties() {
      // Given
      Instant until = Instant.parse("2025-01-01T00:00:00Z");
      LeaseInfo leaseInfo1 = new LeaseInfo("node-1", until, 1);
      LeaseInfo leaseInfo2 = new LeaseInfo("node-2", until, 1);
      LeaseInfo leaseInfo3 = new LeaseInfo("node-1", until.plusSeconds(60), 1);
      LeaseInfo leaseInfo4 = new LeaseInfo("node-1", until, 2);

      // When & Then
      assertThat(leaseInfo1)
          .isNotEqualTo(leaseInfo2)
          .isNotEqualTo(leaseInfo3)
          .isNotEqualTo(leaseInfo4);
    }

    @Test
    @DisplayName("toString 应该包含所有字段")
    void shouldIncludeAllFieldsInToString() {
      // Given
      String owner = "node-1";
      Instant until = Instant.parse("2025-01-01T00:00:00Z");
      int leaseCount = 3;
      LeaseInfo leaseInfo = new LeaseInfo(owner, until, leaseCount);

      // When
      String result = leaseInfo.toString();

      // Then
      assertThat(result)
          .contains("LeaseInfo")
          .contains("owner=" + owner)
          .contains("leasedUntil=" + until)
          .contains("leaseCount=" + leaseCount);
    }
  }

  @Nested
  @DisplayName("工厂方法测试")
  class FactoryMethodsTests {

    @Test
    @DisplayName("none() 应该创建未分配的租约信息")
    void noneShouldCreateUnassignedLeaseInfo() {
      // When
      LeaseInfo leaseInfo = LeaseInfo.none();

      // Then
      assertThat(leaseInfo.owner()).isNull();
      assertThat(leaseInfo.leasedUntil()).isNull();
      assertThat(leaseInfo.leaseCount()).isZero();
      assertThat(leaseInfo.isHeld()).isFalse();
    }

    @Test
    @DisplayName("snapshotOf() 应该创建租约信息快照")
    void snapshotOfShouldCreateLeaseInfoSnapshot() {
      // Given
      String owner = "node-1";
      Instant until = Instant.parse("2025-01-01T00:00:00Z");
      Integer leaseCount = 5;

      // When
      LeaseInfo leaseInfo = LeaseInfo.snapshotOf(owner, until, leaseCount);

      // Then
      assertThat(leaseInfo.owner()).isEqualTo(owner);
      assertThat(leaseInfo.leasedUntil()).isEqualTo(until);
      assertThat(leaseInfo.leaseCount()).isEqualTo(leaseCount);
    }

    @Test
    @DisplayName("snapshotOf() 应该将 null 租约计数转换为零")
    void snapshotOfShouldConvertNullLeaseCountToZero() {
      // Given
      String owner = "node-1";
      Instant until = Instant.now();

      // When
      LeaseInfo leaseInfo = LeaseInfo.snapshotOf(owner, until, null);

      // Then
      assertThat(leaseInfo.leaseCount()).isZero();
    }

    @Test
    @DisplayName("snapshotOf() 应该允许 null owner 和 leasedUntil")
    void snapshotOfShouldAllowNullOwnerAndLeasedUntil() {
      // When
      LeaseInfo leaseInfo = LeaseInfo.snapshotOf(null, null, 3);

      // Then
      assertThat(leaseInfo.owner()).isNull();
      assertThat(leaseInfo.leasedUntil()).isNull();
      assertThat(leaseInfo.leaseCount()).isEqualTo(3);
    }
  }

  @Nested
  @DisplayName("isHeld() 判断方法测试")
  class IsHeldTests {

    @Test
    @DisplayName("应该判断有效 owner 的租约为已持有")
    void shouldReturnTrueForValidOwner() {
      // Given
      LeaseInfo leaseInfo = new LeaseInfo("node-1", Instant.now(), 1);

      // When & Then
      assertThat(leaseInfo.isHeld()).isTrue();
    }

    @Test
    @DisplayName("应该判断 null owner 的租约为未持有")
    void shouldReturnFalseForNullOwner() {
      // Given
      LeaseInfo leaseInfo = new LeaseInfo(null, Instant.now(), 0);

      // When & Then
      assertThat(leaseInfo.isHeld()).isFalse();
    }

    @Test
    @DisplayName("应该判断空白 owner 的租约为未持有")
    void shouldReturnFalseForBlankOwner() {
      // Given
      LeaseInfo emptyOwner = new LeaseInfo("", Instant.now(), 0);
      LeaseInfo whitespaceOwner = new LeaseInfo("   ", Instant.now(), 0);

      // When & Then
      assertThat(emptyOwner.isHeld()).isFalse();
      assertThat(whitespaceOwner.isHeld()).isFalse();
    }
  }

  @Nested
  @DisplayName("acquire() 获取租约测试")
  class AcquireTests {

    @Test
    @DisplayName("应该成功获取未持有的租约")
    void shouldSuccessfullyAcquireUnheldLease() {
      // Given
      LeaseInfo unleased = LeaseInfo.none();
      String newOwner = "node-1";
      Instant until = Instant.parse("2025-01-01T00:00:00Z");

      // When
      LeaseInfo acquired = unleased.acquire(newOwner, until);

      // Then
      assertThat(acquired.owner()).isEqualTo(newOwner);
      assertThat(acquired.leasedUntil()).isEqualTo(until);
      assertThat(acquired.leaseCount()).isEqualTo(1);
      assertThat(acquired.isHeld()).isTrue();
    }

    @Test
    @DisplayName("应该递增租约计数")
    void shouldIncrementLeaseCount() {
      // Given
      LeaseInfo unleased = new LeaseInfo(null, null, 5);
      String newOwner = "node-1";
      Instant until = Instant.now();

      // When
      LeaseInfo acquired = unleased.acquire(newOwner, until);

      // Then
      assertThat(acquired.leaseCount()).isEqualTo(6);
    }

    @Test
    @DisplayName("应该拒绝 null owner 的获取")
    void shouldRejectAcquireWithNullOwner() {
      // Given
      LeaseInfo unleased = LeaseInfo.none();
      Instant until = Instant.now();

      // When & Then
      assertThatThrownBy(() -> unleased.acquire(null, until))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Lease owner must not be blank");
    }

    @Test
    @DisplayName("应该拒绝空白 owner 的获取")
    void shouldRejectAcquireWithBlankOwner() {
      // Given
      LeaseInfo unleased = LeaseInfo.none();
      Instant until = Instant.now();

      // When & Then
      assertThatThrownBy(() -> unleased.acquire("", until))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Lease owner must not be blank");

      assertThatThrownBy(() -> unleased.acquire("   ", until))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Lease owner must not be blank");
    }

    @Test
    @DisplayName("应该拒绝 null 过期时间的获取")
    void shouldRejectAcquireWithNullExpiration() {
      // Given
      LeaseInfo unleased = LeaseInfo.none();

      // When & Then
      assertThatThrownBy(() -> unleased.acquire("node-1", null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Lease expiration must not be null");
    }

    @Test
    @DisplayName("应该拒绝重新获取已持有的租约")
    void shouldRejectReacquireOfHeldLease() {
      // Given
      LeaseInfo held = new LeaseInfo("node-1", Instant.now(), 1);

      // When & Then
      assertThatThrownBy(() -> held.acquire("node-2", Instant.now()))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Lease is already held and cannot be reacquired");
    }

    @Test
    @DisplayName("应该保持原始租约信息不变")
    void shouldKeepOriginalLeaseInfoUnchanged() {
      // Given
      LeaseInfo original = LeaseInfo.none();
      String newOwner = "node-1";
      Instant until = Instant.now();

      // When
      LeaseInfo acquired = original.acquire(newOwner, until);

      // Then
      assertThat(original.owner()).isNull();
      assertThat(original.leasedUntil()).isNull();
      assertThat(original.leaseCount()).isZero();
      assertThat(acquired).isNotSameAs(original);
    }
  }

  @Nested
  @DisplayName("renew() 续约测试")
  class RenewTests {

    @Test
    @DisplayName("应该成功续约已持有的租约")
    void shouldSuccessfullyRenewHeldLease() {
      // Given
      String owner = "node-1";
      Instant oldUntil = Instant.parse("2025-01-01T00:00:00Z");
      LeaseInfo held = new LeaseInfo(owner, oldUntil, 1);
      Instant newUntil = Instant.parse("2025-01-01T01:00:00Z");

      // When
      LeaseInfo renewed = held.renew(owner, newUntil);

      // Then
      assertThat(renewed.owner()).isEqualTo(owner);
      assertThat(renewed.leasedUntil()).isEqualTo(newUntil);
      assertThat(renewed.leaseCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("应该递增租约计数")
    void shouldIncrementLeaseCount() {
      // Given
      String owner = "node-1";
      LeaseInfo held = new LeaseInfo(owner, Instant.now(), 3);

      // When
      LeaseInfo renewed = held.renew(owner, Instant.now());

      // Then
      assertThat(renewed.leaseCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("应该拒绝续约未持有的租约")
    void shouldRejectRenewOfUnheldLease() {
      // Given
      LeaseInfo unleased = LeaseInfo.none();

      // When & Then
      assertThatThrownBy(() -> unleased.renew("node-1", Instant.now()))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Cannot renew because no lease holder is present");
    }

    @Test
    @DisplayName("应该拒绝 null holder 的续约")
    void shouldRejectRenewWithNullHolder() {
      // Given
      LeaseInfo held = new LeaseInfo("node-1", Instant.now(), 1);

      // When & Then
      assertThatThrownBy(() -> held.renew(null, Instant.now()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Lease holder used for renewal must not be blank");
    }

    @Test
    @DisplayName("应该拒绝空白 holder 的续约")
    void shouldRejectRenewWithBlankHolder() {
      // Given
      LeaseInfo held = new LeaseInfo("node-1", Instant.now(), 1);

      // When & Then
      assertThatThrownBy(() -> held.renew("", Instant.now()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Lease holder used for renewal must not be blank");

      assertThatThrownBy(() -> held.renew("   ", Instant.now()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Lease holder used for renewal must not be blank");
    }

    @Test
    @DisplayName("应该拒绝 holder 不匹配的续约")
    void shouldRejectRenewWithMismatchedHolder() {
      // Given
      LeaseInfo held = new LeaseInfo("node-1", Instant.now(), 1);

      // When & Then
      assertThatThrownBy(() -> held.renew("node-2", Instant.now()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Lease holder mismatch; renewal rejected");
    }

    @Test
    @DisplayName("应该拒绝 null 过期时间的续约")
    void shouldRejectRenewWithNullExpiration() {
      // Given
      String owner = "node-1";
      LeaseInfo held = new LeaseInfo(owner, Instant.now(), 1);

      // When & Then
      assertThatThrownBy(() -> held.renew(owner, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Lease expiration must not be null");
    }

    @Test
    @DisplayName("应该保持原始租约信息不变")
    void shouldKeepOriginalLeaseInfoUnchanged() {
      // Given
      String owner = "node-1";
      Instant oldUntil = Instant.parse("2025-01-01T00:00:00Z");
      LeaseInfo original = new LeaseInfo(owner, oldUntil, 1);
      Instant newUntil = Instant.parse("2025-01-01T01:00:00Z");

      // When
      LeaseInfo renewed = original.renew(owner, newUntil);

      // Then
      assertThat(original.leasedUntil()).isEqualTo(oldUntil);
      assertThat(original.leaseCount()).isEqualTo(1);
      assertThat(renewed).isNotSameAs(original);
    }
  }

  @Nested
  @DisplayName("release() 释放租约测试")
  class ReleaseTests {

    @Test
    @DisplayName("应该成功释放已持有的租约")
    void shouldSuccessfullyReleaseHeldLease() {
      // Given
      LeaseInfo held = new LeaseInfo("node-1", Instant.now(), 5);

      // When
      LeaseInfo released = held.release();

      // Then
      assertThat(released.owner()).isNull();
      assertThat(released.leasedUntil()).isNull();
      assertThat(released.leaseCount()).isEqualTo(5);
      assertThat(released.isHeld()).isFalse();
    }

    @Test
    @DisplayName("应该保留租约计数")
    void shouldPreserveLeaseCount() {
      // Given
      int originalCount = 10;
      LeaseInfo held = new LeaseInfo("node-1", Instant.now(), originalCount);

      // When
      LeaseInfo released = held.release();

      // Then
      assertThat(released.leaseCount()).isEqualTo(originalCount);
    }

    @Test
    @DisplayName("应该返回相同实例对于未持有的租约")
    void shouldReturnSameInstanceForUnheldLease() {
      // Given
      LeaseInfo unleased = LeaseInfo.none();

      // When
      LeaseInfo result = unleased.release();

      // Then
      assertThat(result).isSameAs(unleased);
    }

    @Test
    @DisplayName("应该保持原始租约信息不变")
    void shouldKeepOriginalLeaseInfoUnchanged() {
      // Given
      String owner = "node-1";
      Instant until = Instant.now();
      int count = 3;
      LeaseInfo original = new LeaseInfo(owner, until, count);

      // When
      LeaseInfo released = original.release();

      // Then
      assertThat(original.owner()).isEqualTo(owner);
      assertThat(original.leasedUntil()).isEqualTo(until);
      assertThat(original.leaseCount()).isEqualTo(count);
      assertThat(released).isNotSameAs(original);
    }

    @Test
    @DisplayName("应该允许多次释放")
    void shouldAllowMultipleReleases() {
      // Given
      LeaseInfo held = new LeaseInfo("node-1", Instant.now(), 1);

      // When
      LeaseInfo firstRelease = held.release();
      LeaseInfo secondRelease = firstRelease.release();

      // Then
      assertThat(firstRelease.isHeld()).isFalse();
      assertThat(secondRelease).isSameAs(firstRelease);
    }
  }

  @Nested
  @DisplayName("租约生命周期完整流程测试")
  class LeaseLifecycleTests {

    @Test
    @DisplayName("应该支持完整的获取-续约-释放流程")
    void shouldSupportCompleteAcquireRenewReleaseFlow() {
      // Given
      String owner = "node-1";
      Instant firstUntil = Instant.parse("2025-01-01T00:00:00Z");
      Instant secondUntil = Instant.parse("2025-01-01T01:00:00Z");

      // When & Then - 初始状态
      LeaseInfo initial = LeaseInfo.none();
      assertThat(initial.isHeld()).isFalse();
      assertThat(initial.leaseCount()).isZero();

      // 获取租约
      LeaseInfo acquired = initial.acquire(owner, firstUntil);
      assertThat(acquired.isHeld()).isTrue();
      assertThat(acquired.owner()).isEqualTo(owner);
      assertThat(acquired.leaseCount()).isEqualTo(1);

      // 续约
      LeaseInfo renewed = acquired.renew(owner, secondUntil);
      assertThat(renewed.isHeld()).isTrue();
      assertThat(renewed.owner()).isEqualTo(owner);
      assertThat(renewed.leasedUntil()).isEqualTo(secondUntil);
      assertThat(renewed.leaseCount()).isEqualTo(2);

      // 释放
      LeaseInfo released = renewed.release();
      assertThat(released.isHeld()).isFalse();
      assertThat(released.owner()).isNull();
      assertThat(released.leasedUntil()).isNull();
      assertThat(released.leaseCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("应该支持释放后重新获取")
    void shouldSupportReacquireAfterRelease() {
      // Given
      String firstOwner = "node-1";
      String secondOwner = "node-2";
      Instant until = Instant.now();

      LeaseInfo initial = LeaseInfo.none();
      LeaseInfo acquired = initial.acquire(firstOwner, until);
      LeaseInfo released = acquired.release();

      // When
      LeaseInfo reacquired = released.acquire(secondOwner, until);

      // Then
      assertThat(reacquired.owner()).isEqualTo(secondOwner);
      assertThat(reacquired.isHeld()).isTrue();
      assertThat(reacquired.leaseCount()).isEqualTo(2); // 计数保留并递增
    }

    @Test
    @DisplayName("应该支持多次续约")
    void shouldSupportMultipleRenewals() {
      // Given
      String owner = "node-1";
      Instant firstUntil = Instant.parse("2025-01-01T00:00:00Z");
      Instant secondUntil = Instant.parse("2025-01-01T01:00:00Z");
      Instant thirdUntil = Instant.parse("2025-01-01T02:00:00Z");

      LeaseInfo acquired = LeaseInfo.none().acquire(owner, firstUntil);

      // When
      LeaseInfo firstRenew = acquired.renew(owner, secondUntil);
      LeaseInfo secondRenew = firstRenew.renew(owner, thirdUntil);

      // Then
      assertThat(acquired.leaseCount()).isEqualTo(1);
      assertThat(firstRenew.leaseCount()).isEqualTo(2);
      assertThat(secondRenew.leaseCount()).isEqualTo(3);
      assertThat(secondRenew.leasedUntil()).isEqualTo(thirdUntil);
    }
  }
}
