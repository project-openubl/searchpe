import "dayjs/locale/es";
import dayjs from "dayjs";
import localizedFormat from "dayjs/plugin/localizedFormat";
import relativeTime from "dayjs/plugin/relativeTime";
import utc from "dayjs/plugin/utc";

dayjs.extend(utc);
dayjs.extend(relativeTime);
dayjs.extend(localizedFormat);
// Relative times and month names in Spanish (ADR 0005).
dayjs.locale("es");
