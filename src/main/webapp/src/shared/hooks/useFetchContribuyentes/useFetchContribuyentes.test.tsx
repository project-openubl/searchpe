import axios from "axios";
import MockAdapter from "axios-mock-adapter";
import { renderHook, act } from "@testing-library/react-hooks";
import { useFetchContribuyentes } from "./useFetchContribuyentes";
import { Contribuyente, PageRepresentation } from "api/models";
import { CONTRIBUYENTES } from "api/rest";

describe("useFetchContribuyentes", () => {
  it("Fetch error due to no REST API found", async () => {
    // Mock REST API
    new MockAdapter(axios).onGet(CONTRIBUYENTES).networkError();

    // Use hook
    const { result, waitForNextUpdate } = renderHook(() =>
      useFetchContribuyentes()
    );

    const {
      contribuyentes: companies,
      isFetching,
      fetchError,
      fetchContribuyentes: fetchCompanies,
    } = result.current;

    expect(isFetching).toBe(false);
    expect(companies).toBeUndefined();
    expect(fetchError).toBeUndefined();

    // Init fetch
    act(() => fetchCompanies({ page: 2, perPage: 50 }));
    expect(result.current.isFetching).toBe(true);

    // Fetch finished
    await waitForNextUpdate();
    expect(result.current.isFetching).toBe(false);
    expect(result.current.contribuyentes).toBeUndefined();
    expect(result.current.fetchError).not.toBeUndefined();
  });

  it("Fetch success", async () => {
    // Mock REST API
    const data: PageRepresentation<Contribuyente> = {
      meta: {
        offset: 0,
        limit: 0,
        count: 0,
      },
      links: {
        first: "",
        previous: "",
        last: "",
        next: "",
      },
      data: [],
    };

    new MockAdapter(axios)
      .onGet(`${CONTRIBUYENTES}?offset=0&limit=10`)
      .reply(200, data);

    // Use hook
    const { result, waitForNextUpdate } = renderHook(() =>
      useFetchContribuyentes()
    );

    const {
      contribuyentes: companies,
      isFetching,
      fetchError,
      fetchContribuyentes: fetchCompanies,
    } = result.current;

    expect(isFetching).toBe(false);
    expect(companies).toBeUndefined();
    expect(fetchError).toBeUndefined();

    // Init fetch
    act(() => fetchCompanies({ page: 1, perPage: 10 }));
    expect(result.current.isFetching).toBe(true);

    // Fetch finished
    await waitForNextUpdate();
    expect(result.current.isFetching).toBe(false);
    expect(result.current.contribuyentes).toMatchObject(data);
    expect(result.current.fetchError).toBeUndefined();
  });
});
