/*
 * File   : $Source$
 * Date   : $Date$
 * Version: $Revision$
 *
 * This library is part of OpenCms -
 * the Open Source Content Management System
 *
 * Copyright (C) 2002 - 2009 Alkacon Software (http://www.alkacon.com)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * For further information about Alkacon Software, please see the
 * company website: http://www.alkacon.com
 *
 * For further information about OpenCms, please see the
 * project website: http://www.opencms.org
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.opencms.search.solr;

import org.opencms.file.CmsObject;
import org.opencms.file.CmsProperty;
import org.opencms.file.types.CmsResourceTypeBinary;
import org.opencms.file.types.CmsResourceTypeFolder;
import org.opencms.json.JSONObject;
import org.opencms.main.OpenCms;
import org.opencms.report.CmsShellReport;
import org.opencms.search.CmsSearchResource;
import org.opencms.search.documents.CmsDocumentDependency;
import org.opencms.search.fields.CmsSearchField;
import org.opencms.test.OpenCmsTestCase;
import org.opencms.test.OpenCmsTestProperties;
import org.opencms.util.CmsRequestUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.extensions.TestSetup;
import junit.framework.Test;
import junit.framework.TestSuite;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.common.SolrInputDocument;

/**
 * Tests the Solr field mapping.<p>
 * 
 * @since 8.5.0
 */
public class TestSolrFieldConfiguration extends OpenCmsTestCase {

    /**
     * Default JUnit constructor.<p>
     * 
     * @param arg0 JUnit parameters
     */
    public TestSolrFieldConfiguration(String arg0) {

        super(arg0);
    }

    /**
     * Test suite for this test class.<p>
     * 
     * @return the test suite
     */
    public static Test suite() {

        OpenCmsTestProperties.initialize(org.opencms.test.AllTests.TEST_PROPERTIES_PATH);

        TestSuite suite = new TestSuite();
        suite.setName(TestSolrFieldConfiguration.class.getName());
        suite.addTest(new TestSolrFieldConfiguration("testAppinfoSolrField"));
        suite.addTest(new TestSolrFieldConfiguration("testContentLocalesField"));
        suite.addTest(new TestSolrFieldConfiguration("testLocaleDependenciesField"));
        suite.addTest(new TestSolrFieldConfiguration("testDependencies"));
        suite.addTest(new TestSolrFieldConfiguration("testLuceneMigration"));

        TestSetup wrapper = new TestSetup(suite) {

            @Override
            protected void setUp() {

                setupOpenCms("solrtest", "/", "/../org/opencms/search/solr");
            }

            @Override
            protected void tearDown() {

                removeOpenCms();
            }
        };

        return wrapper;
    }

    /**
     * Tests the Solr field configuration that can be done in the XSD of an XML content.<p>
     * 
     * '@see /sites/default/xmlcontent/article.xsd'
     * 
     * @throws Throwable if something goes wrong
     */
    public void testAppinfoSolrField() throws Throwable {

        CmsSolrIndex index = OpenCms.getSearchManager().getIndexSolr(AllTests.SOLR_ONLINE);
        CmsSolrQuery squery = new CmsSolrQuery(
            null,
            CmsRequestUtil.createParameterMap("q=path:/sites/default/xmlcontent/article_0001.html"));
        CmsSolrResultList results = index.search(getCmsObject(), squery);

        /////////////////
        // RESULT TEST // 
        /////////////////

        // Test the result count
        AllTests.printResults(getCmsObject(), results, false);
        assertEquals(1, results.size());

        // Test if the result contains the expected resource 
        CmsSearchResource res = results.get(0);
        assertEquals("/sites/default/xmlcontent/article_0001.html", res.getRootPath());

        ////////////////
        // FIELD TEST // 
        ////////////////

        // Test multiple language field
        String fieldValue = res.getField("ahtml_en");
        assertNotNull(fieldValue);
        fieldValue = res.getField("ahtml_de");
        assertNotNull(fieldValue);

        // Test the contents of the copy fields
        fieldValue = res.getField("test_text_de");
        assertEquals(true, fieldValue.contains("Alkacon Software German"));
        fieldValue = res.getField("test_text_en");
        assertEquals(true, fieldValue.contains("Alkacon Software German"));

        // Test locale restricted field
        fieldValue = res.getField("aauthor_de");
        assertEquals(true, fieldValue.equals("Alkacon Software German"));
        fieldValue = res.getField("aauthor_en");
        assertNull(fieldValue);

        // Test source field
        Date dateValue = res.getDateField("arelease_en_dt");
        assertEquals(true, "1308210520000".equals(new Long(dateValue.getTime()).toString()));
        dateValue = res.getDateField("arelease_de_dt");
        assertEquals(true, "1308210420000".equals(new Long(dateValue.getTime()).toString()));

        // test 'default' value for the whole field
        fieldValue = res.getField("ahomepage_de");
        assertEquals(true, fieldValue.equals("Homepage n.a."));
        fieldValue = res.getField("ahomepage_en");
        assertEquals(true, fieldValue.contains("/sites/default/index.html"));

        // Test the boost to have a complete set of test cases
        // the boost for a field can only be set for "SolrInputDocument"s
        // fields of documents that are returned as query result "SolrDocument"s
        // never have a boost
        float boost = ((SolrInputDocument)res.getDocument().getDocument()).getField("ahtml_en").getBoost();
        assertEquals(true, 1.0F == boost);

        //////////////////
        // MAPPING TEST // 
        //////////////////

        fieldValue = res.getField("Description_de");
        assertEquals(fieldValue, "My Special OpenCms Solr Description");

        // test the 'content' mapping
        fieldValue = res.getField("ateaser_en");
        assertEquals(true, fieldValue.contains("OpenCms Alkacon This is the article 1 text"));

        // test the 'item' mapping with default
        fieldValue = res.getField("ateaser_en");
        assertEquals(true, fieldValue.contains("/sites/default/index.html"));
        fieldValue = res.getField("ateaser_de");
        assertEquals(true, fieldValue.contains("Homepage n.a."));

        // test the property mapping
        fieldValue = res.getField("atitle_en");
        assertEquals(true, fieldValue.contains("Alkacon Software English"));
        fieldValue = res.getField("atitle_de");
        // properties are not localized
        assertEquals(false, fieldValue.contains("Alkacon Software German"));
        assertEquals(true, fieldValue.contains("Alkacon Software English"));
        // but the title item value is localized
        assertEquals(true, fieldValue.contains(">>GermanSearchEgg1<<"));

        // test the 'property-search' mapping
        fieldValue = res.getField("ateaser_en");
        assertEquals(true, fieldValue.contains("Cologne is a nice city"));

        // test the 'attribute' mapping
        fieldValue = res.getField("ateaser_en");
        // This is the Lucene optimized String representaion of the date
        assertEquals(true, fieldValue.contains("20110616074840000"));

        // test the 'dynamic' mapping with 'class' attribute
        fieldValue = res.getField("ateaser_en");
        assertEquals(true, fieldValue.contains("This is an amazing and very 'dynamic' content"));

        squery = new CmsSolrQuery(
            null,
            CmsRequestUtil.createParameterMap("q=path:/sites/default/xmlcontent/article_0002.html"));
        results = index.search(getCmsObject(), squery);
        res = results.get(0);
        assertEquals("/sites/default/xmlcontent/article_0002.html", res.getRootPath());
        assertEquals(true, res.getMultivaluedField("ateaser2_en_txt").contains("This is teaser 2 in sample article 2."));
    }

    /**
     * Tests the locales stored in the index.<p>
     * 
     * @throws Throwable if something goes wrong
     */
    public void testContentLocalesField() throws Throwable {

        Map<String, List<String>> filenames = new HashMap<String, List<String>>();
        filenames.put("rabbit_en_GB.html", Collections.singletonList("en_GB"));
        filenames.put("rabbit_en_GB", Collections.singletonList("en_GB"));
        filenames.put("rabbit_en.html", Collections.singletonList("en"));
        filenames.put("rabbit_en", Collections.singletonList("en"));
        filenames.put("rabbit_en.", Collections.singletonList("en"));
        filenames.put("rabbit_enr", Arrays.asList(new String[] {"en"}));
        filenames.put("rabbit_en.tar.gz", Arrays.asList(new String[] {"en"}));
        filenames.put("rabbit_de_en_GB.html", Collections.singletonList("en_GB"));
        filenames.put("rabbit_de_DE_EN_DE_en.html", Collections.singletonList("en"));
        filenames.put("rabbit_de_DE_EN_en_DE.html", Collections.singletonList("en_DE"));

        // create test folder
        String folderName = "/filenameTest/";
        CmsObject cms = getCmsObject();
        cms.createResource(folderName, CmsResourceTypeFolder.RESOURCE_TYPE_ID, null, null);
        cms.unlockResource(folderName);
        for (String filename : filenames.keySet()) {
            // create master resource
            importTestResource(
                cms,
                "org/opencms/search/pdf-test-112.pdf",
                folderName + filename,
                CmsResourceTypeBinary.getStaticTypeId(),
                Collections.<CmsProperty> emptyList());
        }
        // publish the project and update the search index
        OpenCms.getPublishManager().publishProject(cms, new CmsShellReport(cms.getRequestContext().getLocale()));
        OpenCms.getPublishManager().waitWhileRunning();

        CmsSolrIndex index = OpenCms.getSearchManager().getIndexSolr(AllTests.SOLR_ONLINE);
        CmsSolrQuery query = new CmsSolrQuery();
        query.setSearchRoots(cms.getRequestContext().addSiteRoot(folderName));
        CmsSolrResultList results = index.search(cms, query);
        AllTests.printResults(cms, results, false);
        assertEquals(10, results.getNumFound());

        for (Map.Entry<String, List<String>> filename : filenames.entrySet()) {
            String absoluteFileName = cms.getRequestContext().addSiteRoot(folderName + filename.getKey());
            SolrQuery squery = new CmsSolrQuery();
            squery.addFilterQuery("path:" + absoluteFileName);
            results = index.search(cms, squery);
            assertEquals(1, results.size());
            CmsSearchResource res = results.get(0);
            List<String> fieldLocales = res.getMultivaluedField(CmsSearchField.FIELD_CONTENT_LOCALES);
            assertTrue(fieldLocales.size() == filename.getValue().size());
            assertTrue(fieldLocales.containsAll(filename.getValue()));
            assertTrue(filename.getValue().containsAll(fieldLocales));
        }

        SolrQuery squery = new CmsSolrQuery();
        squery.addFilterQuery("path:" + "/sites/default/xmlcontent/article_0004.html");
        results = index.search(cms, squery);
        assertEquals(1, results.size());
        CmsSearchResource res = results.get(0);
        List<String> fieldLocales = res.getMultivaluedField(CmsSearchField.FIELD_CONTENT_LOCALES);
        assertTrue(fieldLocales.size() == 1);
        fieldLocales.contains(Collections.singletonList("en"));
    }

    /**
     * 
     * @throws Throwable
     */
    public void testDependencies() throws Throwable {

        List<String> filenames = new ArrayList<String>();
        filenames.add("search_rabbit.pdf");
        filenames.add("search_rabbit_vi.pdf");
        filenames.add("search_rabbit_pt.pdf");
        filenames.add("search_rabbit_fr.pdf");
        filenames.add("search_rabbit_fr_001.pdf");
        filenames.add("search_rabbit_fr_002.pdf");

        // create test folder
        String folderName = "/testLocaleVariants/";
        CmsObject cms = getCmsObject();
        cms.createResource(folderName, CmsResourceTypeFolder.RESOURCE_TYPE_ID, null, null);
        cms.unlockResource(folderName);
        for (String filename : filenames) {
            importTestResource(
                cms,
                "org/opencms/search/pdf-test-112.pdf",
                folderName + filename,
                CmsResourceTypeBinary.getStaticTypeId(),
                Collections.<CmsProperty> emptyList());
        }
        // publish the project and update the search index
        OpenCms.getPublishManager().publishProject(cms, new CmsShellReport(cms.getRequestContext().getLocale()));
        OpenCms.getPublishManager().waitWhileRunning();

        CmsSolrIndex index = OpenCms.getSearchManager().getIndexSolr(AllTests.SOLR_ONLINE);
        CmsSolrQuery query = new CmsSolrQuery();
        query.setSearchRoots(Collections.singletonList(cms.getRequestContext().addSiteRoot(folderName)));
        CmsSolrResultList results = index.search(cms, query, false);
        AllTests.printResults(cms, results, false);
        int count = 0;
        for (CmsSearchResource res : results) {
            System.out.println("---------------------");
            System.out.println("DOCUMENT: " + ++count);
            System.out.println("---------------------");

            System.out.println("TYPE: " + res.getField("dependencyType"));
            String depField = res.getField("dep_document");
            if (depField != null) {
                System.out.println("DEP_DOC: " + depField);
                CmsDocumentDependency d = CmsDocumentDependency.fromDependencyString(depField, res.getRootPath());
                String depCreated = d.toDependencyString(cms);
                assertEquals(depField, depCreated);
            }

            List<String> variants = res.getMultivaluedField("dep_variant");
            if (variants != null) {
                System.out.println("---");
                System.out.println("VARIANTS");
                for (String varField : variants) {
                    System.out.println("DEP_VAR: " + varField);
                    JSONObject varJson = new JSONObject(varField);
                    String path = varJson.getString("path");
                    CmsDocumentDependency var = CmsDocumentDependency.fromDependencyString(varField, path);
                    String varCreated = var.toDependencyString(cms);
                    assertEquals(varField, varCreated);
                }
            }

            List<String> attachments = res.getMultivaluedField("dep_attachment");
            if (attachments != null) {
                System.out.println("---");
                System.out.println("ATTACHMENTS");
                for (String attField : attachments) {
                    System.out.println("DEP_ATT: " + attField);
                    JSONObject attJson = new JSONObject(attField);
                    String path = attJson.getString("path");
                    CmsDocumentDependency att = CmsDocumentDependency.fromDependencyString(attField, path);
                    String varCreated = att.toDependencyString(cms);
                    assertEquals(attField, varCreated);
                }
            }
        }
    }

    /**
     * 
     * @throws Throwable
     */
    public void testLocaleDependenciesField() throws Throwable {

        Map<String, List<String>> filenames = new HashMap<String, List<String>>();
        filenames.put("search_rabbit.pdf", Collections.singletonList("de_DE"));
        filenames.put("search_rabbit_de.pdf", Collections.singletonList("de_DE"));
        filenames.put("search_rabbit_de_001.pdf", Collections.singletonList("de_DE"));
        filenames.put("search_rabbit_de_002.pdf", Collections.singletonList("de_DE"));
        filenames.put("search_rabbit_de_003.pdf", Collections.singletonList("de_DE"));
        filenames.put("search_rabbit_de_004.pdf", Collections.singletonList("de_DE"));
        filenames.put("search_rabbit_0001.pdf", Collections.singletonList("de_DE"));
        filenames.put("search_rabbit_0002.pdf", Collections.singletonList("de_DE"));
        filenames.put("search_rabbit_0003.pdf", Collections.singletonList("de_DE"));

        filenames.put("search_rabbit_en.pdf", Collections.singletonList("en_EN"));
        filenames.put("search_rabbit_en_001.pdf", Collections.singletonList("en_EN"));
        filenames.put("search_rabbit_en_002.pdf", Collections.singletonList("en_EN"));
        filenames.put("search_rabbit_en_003.pdf", Collections.singletonList("en_EN"));
        filenames.put("search_rabbit_en_004.pdf", Collections.singletonList("en_EN"));

        filenames.put("search_rabbit2_0001.pdff", Collections.singletonList("de_DE"));
        filenames.put("search_rabbit2_0001.pdf", Collections.singletonList("de_DE"));

        filenames.put("tema_00001.html", Collections.singletonList("de_DE"));
        filenames.put("ar_00001.xml", Collections.singletonList("de_DE"));

        // create test folder
        String folderName = "/filenameTest2/";
        CmsObject cms = getCmsObject();
        cms.createResource(folderName, CmsResourceTypeFolder.RESOURCE_TYPE_ID, null, null);
        cms.unlockResource(folderName);
        for (String filename : filenames.keySet()) {
            // create master resource
            importTestResource(
                cms,
                "org/opencms/search/pdf-test-112.pdf",
                folderName + filename,
                CmsResourceTypeBinary.getStaticTypeId(),
                Collections.<CmsProperty> emptyList());
        }
        // publish the project and update the search index
        OpenCms.getPublishManager().publishProject(cms, new CmsShellReport(cms.getRequestContext().getLocale()));
        OpenCms.getPublishManager().waitWhileRunning();

        CmsSolrIndex index = OpenCms.getSearchManager().getIndexSolr(AllTests.SOLR_ONLINE);
        CmsSolrQuery query = new CmsSolrQuery();
        query.setSearchRoots(Collections.singletonList(cms.getRequestContext().addSiteRoot(folderName)));
        CmsSolrResultList results = index.search(cms, query, false);
        AllTests.printResults(cms, results, false);
        // assertEquals(10, results.getNumFound());

        for (Map.Entry<String, List<String>> filename : filenames.entrySet()) {
            String absoluteFileName = cms.getRequestContext().addSiteRoot(folderName + filename.getKey());
            SolrQuery squery = new CmsSolrQuery();
            squery.addFilterQuery("path:" + absoluteFileName);
            results = index.search(cms, squery);
        }
    }

    /**
     * Tests if the field configuration in the 'opencms-search.xml' can also be used for Solr.<p>
     * 
     * @throws Throwable if something goes wrong
     */
    public void testLuceneMigration() throws Throwable {

        CmsSolrIndex index = OpenCms.getSearchManager().getIndexSolr(AllTests.SOLR_ONLINE);

        CmsSolrFieldConfiguration conf = (CmsSolrFieldConfiguration)index.getFieldConfiguration();
        assertNotNull(conf.getSolrFields().get("meta_txt"));
        assertNotNull(conf.getSolrFields().get("description_txt"));
        assertNotNull(conf.getSolrFields().get("keywords_txt"));
        assertNotNull(conf.getSolrFields().get("special_txt"));

        CmsSolrQuery squery = new CmsSolrQuery(
            null,
            CmsRequestUtil.createParameterMap("q=path:/sites/default/xmlcontent/article_0001.html"));
        CmsSolrResultList results = index.search(getCmsObject(), squery);

        CmsSearchResource res = results.get(0);
        String value = "Sample article 1  (>>SearchEgg1<<)";
        List<String> metaValue = res.getMultivaluedField("meta_txt");
        assertEquals(value, metaValue.get(0));
    }
}
