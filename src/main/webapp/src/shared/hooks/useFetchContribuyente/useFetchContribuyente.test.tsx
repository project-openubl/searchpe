import axios from "axios";
import MockAdapter from "axios-mock-adapter";
import { renderHook, act } from "@testing-library/react-hooks";
import { useFetchContribuyente } from "./useFetchContribuyente";
import { Contribuyente } from "api/models";
import { CONTRIBUYENTES } from "api/rest";

describe("useFetchContribuyente", () => {
  it("Fetch error due to no REST API found", async () => {
    const RUC = "12345678910";

    // Mock REST API
    new MockAdapter(axios).onGet(`${CONTRIBUYENTES}/${RUC}`).networkError();

    // Use hook
    const { result, waitForNextUpdate } = renderHook(() =>
      useFetchContribuyente()
    );

    const {
      contribuyente,
      isFetching,
      fetchError,
      fetchContribuyente,
    } = result.current;

    expect(isFetching).toBe(false);
    expect(contribuyente).toBeUndefined();
    expect(fetchError).toBeUndefined();

    // Init fetch
    act(() => fetchContribuyente(RUC));
    expect(result.current.isFetching).toBe(true);

    // Fetch finished
    await waitForNextUpdate();
    expect(result.current.isFetching).toBe(false);
    expect(result.current.contribuyente).toBeUndefined();
    expect(result.current.fetchError).not.toBeUndefined();
  });

  it("Fetch success", async () => {
    // Mock REST API
    const data: Contribuyente = {
      ruc: "12345678910",
      razonSocial: "my empresa",
      estadoContribuyente: "ACTIVO",
      ubigeo: "123456",
    };

    new MockAdapter(axios)
      .onGet(`${CONTRIBUYENTES}/${data.ruc}`)
      .reply(200, data);

    // Use hook
    const { result, waitForNextUpdate } = renderHook(() =>
      useFetchContribuyente()
    );

    const {
      contribuyente,
      isFetching,
      fetchError,
      fetchContribuyente,
    } = result.current;

    expect(isFetching).toBe(false);
    expect(contribuyente).toBeUndefined();
    expect(fetchError).toBeUndefined();

    // Init fetch
    act(() => fetchContribuyente(data.ruc));
    expect(result.current.isFetching).toBe(true);

    // Fetch finished
    await waitForNextUpdate();
    expect(result.current.isFetching).toBe(false);
    expect(result.current.contribuyente).toMatchObject(data);
    expect(result.current.fetchError).toBeUndefined();
  });
});
