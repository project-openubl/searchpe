import axios from "axios";
import MockAdapter from "axios-mock-adapter";
import { renderHook, act } from "@testing-library/react-hooks";
import { useFetchVersions } from "./useFetchVersions";
import { Version } from "api/models";
import { VERSIONS } from "api/rest";

describe("useFetchVersions", () => {
  it("Fetch error due to no REST API found", async () => {
    // Mock REST API
    new MockAdapter(axios).onGet(VERSIONS).networkError();

    // Use hook
    const { result, waitForNextUpdate } = renderHook(() => useFetchVersions());

    const { versions, isFetching, fetchError, fetchVersions } = result.current;

    expect(isFetching).toBe(false);
    expect(versions).toBeUndefined();
    expect(fetchError).toBeUndefined();

    // Init fetch
    act(() => fetchVersions({ watch: false }));
    expect(result.current.isFetching).toBe(true);

    // Fetch finished
    await waitForNextUpdate();
    expect(result.current.isFetching).toBe(false);
    expect(result.current.versions).toBeUndefined();
    expect(result.current.fetchError).not.toBeUndefined();
  });

  it("Fetch success", async () => {
    // Mock REST API
    const data: Version[] = [
      {
        id: 1,
        status: "COMPLETED",
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      },
    ];

    new MockAdapter(axios).onGet(VERSIONS).reply(200, data);

    // Use hook
    const { result, waitForNextUpdate } = renderHook(() => useFetchVersions());

    const { versions, isFetching, fetchError, fetchVersions } = result.current;

    expect(isFetching).toBe(false);
    expect(versions).toBeUndefined();
    expect(fetchError).toBeUndefined();

    // Init fetch
    act(() => fetchVersions({ watch: false }));
    expect(result.current.isFetching).toBe(true);

    // Fetch finished
    await waitForNextUpdate();
    expect(result.current.isFetching).toBe(false);
    expect(result.current.versions).toMatchObject(data);
    expect(result.current.fetchError).toBeUndefined();
  });
});
