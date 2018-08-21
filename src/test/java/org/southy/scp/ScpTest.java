package org.southy.scp;

import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.Test;
import java.io.File;
import java.io.IOException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class ScpTest {

    @Test
    public void testDownload() {
        File dir = new File("src/test/resources/test-dir");
        if (dir.exists() || dir.mkdirs()) {
            Scp scp = Scp
                    .download("readme.txt", dir, "test.rebex.net", "demo")
                    .password("password")
                    .strictHostKeyChecking(false)
                    .execute();
            assertThat(scp.success(), is(true));
            assertThat(new File(dir, "readme.txt").exists(), is(true));
        }
    }

    @AfterClass
    public static void afterClass() throws IOException {
        FileUtils.forceDelete(new File("src/test/resources/test-dir"));
    }

}