package io.github.projectopenubl.xhandler.pdf;

/** Test access to package-private helpers. */
public final class DocumentPdfAccess {

    private DocumentPdfAccess() {}

    public static String banner(String status) {
        return DocumentPdf.banner(status);
    }
}
