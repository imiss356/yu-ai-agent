package com.yuaiagent.rag;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DocQADocumentLoaderTest
{
    @Resource
    private DocQADocumentLoader docQADocumentLoader;

    @Test
    void loadMarkdowns()
    {
        docQADocumentLoader.loadMarkdowns();
    }
}