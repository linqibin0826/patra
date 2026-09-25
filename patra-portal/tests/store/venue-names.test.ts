import { beforeEach, describe, expect, it } from "vitest";
import { useVenueNamesStore } from "@/store/venue-names";

describe("useVenueNamesStore", () => {
  beforeEach(() => useVenueNamesStore.setState({ names: {} }));

  it("remember 记录 id → 刊名，后写覆盖先写", () => {
    useVenueNamesStore.getState().remember("1", "Nature");
    useVenueNamesStore.getState().remember("2", "Cell");
    useVenueNamesStore.getState().remember("1", "Nature Medicine");
    expect(useVenueNamesStore.getState().names).toEqual({ "1": "Nature Medicine", "2": "Cell" });
  });
});
