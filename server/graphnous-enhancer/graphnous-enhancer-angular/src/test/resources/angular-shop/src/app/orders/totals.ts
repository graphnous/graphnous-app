export class Totals {
  private readonly values = new Map<string, number>();
  get(key: string) { return this.values.get(key); }
}
