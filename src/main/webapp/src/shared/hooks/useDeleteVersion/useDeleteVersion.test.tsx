import axios from "axios";
import MockAdapter from "axios-mock-adapter";
import { renderHook, act } from "@testing-library/react-hooks";
import { useDeleteVersion } from "./useDeleteVersion";
import { Version } from "api/models";
import { VERSIONS } from "api/rest";

describe("useDeleteVersion", () => {
  it("Valid initial status", () => {
    // Use hook
    const { result } = renderHook(() => useDeleteVersion());

    const { isDeleting, deleteVersion: deleteCompany } = result.current;

    expect(isDeleting).toBe(false);
    expect(deleteCompany).toBeDefined();
  });

  it("Delete error", async () => {
    const version: Version = {
      id: 1,
      status: "COMPLETED",
      createdAt: 1,
      updatedAt: 1,
    };
    const onSuccessMock = jest.fn();
    const onErrorMock = jest.fn();

    // Mock REST API
    new MockAdapter(axios).onDelete(`${VERSIONS}/${version.id}`).networkError();

    // Use hook
    const { result, waitForNextUpdate } = renderHook(() => useDeleteVersion());
    const { deleteVersion: deleteCompany } = result.current;

    // Init delete
    act(() => deleteCompany(version, onSuccessMock, onErrorMock));
    expect(result.current.isDeleting).toBe(true);

    // Delete finished
    await waitForNextUpdate();
    expect(result.current.isDeleting).toBe(false);
    expect(onSuccessMock).toHaveBeenCalledTimes(0);
    expect(onErrorMock).toHaveBeenCalledTimes(1);
  });

  it("Delete success", async () => {
    const version: Version = {
      id: 1,
      status: "COMPLETED",
      createdAt: 1,
      updatedAt: 1,
    };
    const onSuccessMock = jest.fn();
    const onErrorMock = jest.fn();

    // Mock REST API
    new MockAdapter(axios).onDelete(`${VERSIONS}/${version.id}`).reply(201);

    // Use hook
    const { result, waitForNextUpdate } = renderHook(() => useDeleteVersion());
    const { deleteVersion: deleteCompany } = result.current;

    // Init delete
    act(() => deleteCompany(version, onSuccessMock, onErrorMock));
    expect(result.current.isDeleting).toBe(true);

    // Delete finished
    await waitForNextUpdate();
    expect(result.current.isDeleting).toBe(false);
    expect(onSuccessMock).toHaveBeenCalledTimes(1);
    expect(onErrorMock).toHaveBeenCalledTimes(0);
  });
});
