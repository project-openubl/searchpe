package io.searchpe.model.search;

import io.searchpe.model.Company;
import org.apache.lucene.analysis.core.KeywordTokenizerFactory;
import org.apache.lucene.analysis.core.LowerCaseFilterFactory;
import org.apache.lucene.analysis.core.StopFilterFactory;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilterFactory;
import org.apache.lucene.analysis.miscellaneous.WordDelimiterFilterFactory;
import org.apache.lucene.analysis.ngram.EdgeNGramFilterFactory;
import org.apache.lucene.analysis.ngram.NGramFilterFactory;
import org.apache.lucene.analysis.pattern.PatternReplaceFilterFactory;
import org.apache.lucene.analysis.standard.StandardFilterFactory;
import org.apache.lucene.analysis.standard.StandardTokenizerFactory;
import org.hibernate.search.annotations.Analyze;
import org.hibernate.search.annotations.Factory;
import org.hibernate.search.annotations.Index;
import org.hibernate.search.annotations.Store;
import org.hibernate.search.cfg.SearchMapping;

import java.lang.annotation.ElementType;

public class CompanyMappingFactory {

    @Factory
    public SearchMapping getSearchMapping() {
        SearchMapping mapping = new SearchMapping();

        mapping
                .analyzerDef("staticTextAnalyzer", StandardTokenizerFactory.class)
                .filter(StandardFilterFactory.class)
                .filter(LowerCaseFilterFactory.class)
                .filter(ASCIIFoldingFilterFactory.class);
        mapping
                .analyzerDef("nameTextAnalyzer", StandardTokenizerFactory.class)
                .filter(StandardFilterFactory.class)
                .filter(LowerCaseFilterFactory.class)
                .filter(ASCIIFoldingFilterFactory.class);
        mapping
                .analyzerDef("standardAnalyzer", StandardTokenizerFactory.class)
                .filter(WordDelimiterFilterFactory.class)
                .filter(LowerCaseFilterFactory.class)
                .filter(PatternReplaceFilterFactory.class)
                .param("pattern", "([^a-zA-Z0-9\\.])")
                .param("replacement", " ")
                .param("pattern", "all");
        mapping
                .analyzerDef("autocompleteNGramAnalyzer", StandardTokenizerFactory.class)
                .filter(WordDelimiterFilterFactory.class)
                .filter(LowerCaseFilterFactory.class)
                .filter(NGramFilterFactory.class)
                .param("minGramSize", "3")
                .param("maxGramSize", "5")
                .filter(PatternReplaceFilterFactory.class)
                .param("pattern", "([^a-zA-Z0-9\\.])")
                .param("replacement", " ")
                .param("pattern", "all");
        mapping
                .analyzerDef("autocompleteEdgeAnalyzer", KeywordTokenizerFactory.class)
                .filter(PatternReplaceFilterFactory.class)
                .param("pattern", "([^a-zA-Z0-9\\.])")
                .param("replacement", " ")
                .param("pattern", "all")
                .filter(LowerCaseFilterFactory.class)
                .filter(StopFilterFactory.class)
                .filter(EdgeNGramFilterFactory.class)
                .param("minGramSize", "3")
                .param("maxGramSize", "50");

        mapping.entity(Company.class)
                .indexed()

                .property("id", ElementType.FIELD).documentId().name("id")

                .property("ruc", ElementType.FIELD).field().name("ruc").analyze(Analyze.NO)
                .property("estadoContribuyente", ElementType.FIELD).field().name("estadoContribuyente").analyze(Analyze.NO)
                .property("condicionDomicilio", ElementType.FIELD).field().name("condicionDomicilio").analyze(Analyze.NO)
                .property("ubigeo", ElementType.FIELD).field().name("ubigeo").analyze(Analyze.NO)

                .property("razonSocial", ElementType.FIELD)
                .field().name("razonSocial").index(Index.YES).store(Store.YES).analyze(Analyze.YES).analyzer("standardAnalyzer")
                .field().name("nGramRazonSocial").index(Index.YES).store(Store.NO).analyze(Analyze.YES).analyzer("autocompleteNGramAnalyzer")
                .field().name("edgeNGramRazonSocial").index(Index.YES).store(Store.NO).analyze(Analyze.YES).analyzer("autocompleteEdgeAnalyzer");

        return mapping;
    }

}
