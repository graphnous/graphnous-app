export interface Page<T> {
    content: T[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number
}

export const emptyPage = <T>(): Page<T> => {
    return {
        content: [],
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0
    }
}