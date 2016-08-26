package bamboo.app;

import bamboo.core.*;
import bamboo.crawl.*;
import bamboo.directory.Categories;
import bamboo.pandas.Pandas;
import bamboo.seedlist.Seedlists;
import bamboo.task.*;

import java.io.PrintWriter;

public class Bamboo implements AutoCloseable {

    public final Config config;
    private final DbPool dbPool;
    public final Crawls crawls;
    public final Serieses serieses;
    public final Warcs warcs;
    public final Collections collections;
    public final Seedlists seedlists;
    public final Pandas pandas;
    public final Categories categories;

    public final Taskmaster taskmaster;
    private final CdxIndexer cdxIndexer;
    private final SolrIndexer solrIndexer;

    public Bamboo() {
        this(new Config());
    }

    public Bamboo(Config config) {
        long startTime = System.currentTimeMillis();

        this.config = config;

        dbPool = new DbPool(config);
        dbPool.migrate();
        DAO dao = dbPool.dao();

        this.taskmaster = new Taskmaster();

        // crawl package
        this.serieses = new Serieses(dao.serieses());
        this.warcs = new Warcs(dao.warcs());
        this.crawls = new Crawls(dao.crawls(), serieses, warcs);
        this.collections = new Collections(dao.collections());

        // directory pacakge
        this.categories = new Categories(dao.directory());

        // seedlist package
        this.seedlists = new Seedlists(dao.seedlists());

        // task package
        taskmaster.add(new Importer(config, crawls));
        cdxIndexer = new CdxIndexer(warcs, crawls, serieses, collections);
        taskmaster.add(cdxIndexer);
        solrIndexer = new SolrIndexer(collections, crawls, warcs);
        taskmaster.add(solrIndexer);
        taskmaster.add(new WatchImporter(collections, crawls, cdxIndexer, warcs, config.getWatches()));

        // pandas package
        if (config.getPandasDbUrl() != null) {
            pandas = new Pandas(config, crawls, seedlists);
        } else {
            pandas = null;
        }

        System.out.println("Initialized Bamboo in " + (System.currentTimeMillis() - startTime) + "ms");
    }

    public void close() {
        taskmaster.close();
        dbPool.close();
        pandas.close();
    }

    public boolean healthcheck(PrintWriter out) {
        return dbPool.healthcheck(out) &
                warcs.healthcheck(out) &
                cdxIndexer.healthcheck(out) &
                solrIndexer.healthcheck(out);
    }
}
