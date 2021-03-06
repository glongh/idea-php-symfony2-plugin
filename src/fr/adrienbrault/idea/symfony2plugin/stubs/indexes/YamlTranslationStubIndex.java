package fr.adrienbrault.idea.symfony2plugin.stubs.indexes;

import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.psi.PsiFile;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import fr.adrienbrault.idea.symfony2plugin.Symfony2ProjectComponent;
import fr.adrienbrault.idea.symfony2plugin.stubs.indexes.externalizer.StringSetDataExternalizer;
import fr.adrienbrault.idea.symfony2plugin.translation.collector.YamlTranslationVistor;
import fr.adrienbrault.idea.symfony2plugin.translation.dict.TranslationUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLFileType;
import org.jetbrains.yaml.psi.YAMLFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


public class YamlTranslationStubIndex extends FileBasedIndexExtension<String, Set<String>> {

    public static final ID<String, Set<String>> KEY = ID.create("fr.adrienbrault.idea.symfony2plugin.translations");
    private final KeyDescriptor<String> myKeyDescriptor = new EnumeratorStringDescriptor();

    @NotNull
    @Override
    public DataIndexer<String, Set<String>, FileContent> getIndexer() {

        return new DataIndexer<String, Set<String>, FileContent>() {
            @NotNull
            @Override
            public Map<String, Set<String>> map(@NotNull FileContent inputData) {

                Map<String, Set<String>> map = new THashMap<>();

                if(!Symfony2ProjectComponent.isEnabledForIndex(inputData.getProject())) {
                    return map;
                }

                String extension = inputData.getFile().getExtension();
                if("xlf".equalsIgnoreCase(extension) || "xliff".equalsIgnoreCase(extension)) {
                    return getXlfStringMap(inputData, map);
                }

                PsiFile psiFile = inputData.getPsiFile();
                if(!(psiFile instanceof YAMLFile)) {
                    return map;
                }

                // check physical file position
                if (!isValidTranslationFile(inputData, psiFile)) {
                    return map;
                }

                String domainName = this.getDomainName(inputData.getFileName());
                if(domainName == null) {
                    return map;
                }

                final Set<String> translationKeySet = new HashSet<>();
                YamlTranslationVistor.collectFileTranslations((YAMLFile) psiFile, (keyName, yamlKeyValue) -> {
                    translationKeySet.add(keyName);
                    return true;
                });

                if(translationKeySet.size() == 0) {
                    return map;
                }

                map.put(domainName, translationKeySet);

                return map;

            }

            private boolean isValidTranslationFile(FileContent inputData, PsiFile psiFile) {

                // dont index all yaml files; "Resources/translations" should be good for now
                String relativePath = VfsUtil.getRelativePath(inputData.getFile(), psiFile.getProject().getBaseDir(), '/');
                if(relativePath != null) {
                    return relativePath.contains("Resources/translations");
                }

                // Resources/translations/messages.de.yml
                // @TODO: Resources/translations/de/messages.yml
                String path = inputData.getFile().getPath();
                if(path.endsWith("Resources/translations/" + inputData.getFileName())) {
                    return true;
                }

                return false;
            }

            private Map<String, Set<String>> getXlfStringMap(FileContent inputData, Map<String, Set<String>> map) {

                // testing files are not that nice
                String relativePath = VfsUtil.getRelativePath(inputData.getFile(), inputData.getProject().getBaseDir(), '/');
                if(relativePath != null && (relativePath.contains("/Test/") || relativePath.contains("/Tests/") || relativePath.contains("/Fixture/") || relativePath.contains("/Fixtures/"))) {
                    return map;
                }

                String domainName = this.getDomainName(inputData.getFileName());
                if(domainName == null) {
                    return map;
                }

                InputStream inputStream;
                try {
                    inputStream = inputData.getFile().getInputStream();
                } catch (IOException e) {
                    return map;
                }

                Set<String> set = TranslationUtil.getXliffTranslations(inputStream);
                if(set.size() > 0) {
                    map.put(domainName, set);
                }

                return map;
            }

            @Nullable
            private String getDomainName(String fileName) {
                int domainSplit = fileName.indexOf(".");
                if(domainSplit < 0) {
                    return null;
                }

                return fileName.substring(0, domainSplit);
            }

        };
    }

    @NotNull
    @Override
    public ID<String, Set<String>> getName() {
        return KEY;
    }


    @NotNull
    @Override
    public KeyDescriptor<String> getKeyDescriptor() {
        return this.myKeyDescriptor;
    }

    @NotNull
    public DataExternalizer<Set<String>> getValueExternalizer() {
        return new StringSetDataExternalizer();
    }

    @NotNull
    @Override
    public FileBasedIndex.InputFilter getInputFilter() {
        return file ->
            file.getFileType() == YAMLFileType.YML || "xlf".equalsIgnoreCase(file.getExtension()) || "xliff".equalsIgnoreCase(file.getExtension());
    }

    @Override
    public boolean dependsOnFileContent() {
        return true;
    }

    @Override
    public int getVersion() {
        return 4;
    }

}
