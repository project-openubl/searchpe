import axios from "axios";
import MockAdapter from "axios-mock-adapter";
import { renderHook, act } from "@testing-library/react-hooks";
import { useFetchVersion } from "./useFetchVersion";
import { Version } from "api/models";
import { VERSIONS } from "api/rest";

describe("useFetchVersion", () => {
  it("Fetch error due to no REST API found", async () => {
    const RUC = "12345678910";

    // Mock REST API
    new MockAdapter(axios).onGet(`${VERSIONS}/${RUC}`).networkError();

    // Use hook
    const { result, waitForNextUpdate } = renderHook(() => useFetchVersion());

    const { version, isFetching, fetchError, fetchVersion } = result.current;

    expect(isFetching).toBe(false);
    expect(version).toBeUndefined();
    expect(fetchError).toBeUndefined();

    // Init fetch
    act(() => fetchVersion(RUC));
    expect(result.current.isFetching).toBe(true);

    // Fetch finished
    await waitForNextUpdate();
    expect(result.current.isFetching).toBe(false);
    expect(result.current.version).toBeUndefined();
    expect(result.current.fetchError).not.toBeUndefined();
  });

  it("Fetch success", async () => {
    // Mock REST API
    const data: Version = {
      id: 1,
      status: "COMPLETED",
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };

    new MockAdapter(axios).onGet(`${VERSIONS}/${data.id}`).reply(200, data);

    // Use hook
    const { result, waitForNextUpdate } = renderHook(() => useFetchVersion());

    const { version, isFetching, fetchError, fetchVersion } = result.current;

    expect(isFetching).toBe(false);
    expect(version).toBeUndefined();
    expect(fetchError).toBeUndefined();

    // Init fetch
    act(() => fetchVersion(data.id));
    expect(result.current.isFetching).toBe(true);

    // Fetch finished
    await waitForNextUpdate();
    expect(result.current.isFetching).toBe(false);
    expect(result.current.version).toMatchObject(data);
    expect(result.current.fetchError).toBeUndefined();
  });
});
