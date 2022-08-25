package uk.ac.ebi.pride.toolsuite.px_validator.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import uk.ac.ebi.jmzidml.model.mzidml.SpectraData;
import uk.ac.ebi.pride.tools.jmzreader.JMzReader;
import uk.ac.ebi.pride.tools.jmzreader.JMzReaderException;
import uk.ac.ebi.pride.tools.jmzreader.model.Spectrum;
import uk.ac.ebi.pride.tools.mgf_parser.MgfFile;
import uk.ac.ebi.pride.tools.ms2_parser.Ms2File;
import uk.ac.ebi.pride.tools.mzdata_wrapper.MzMlWrapper;
import uk.ac.ebi.pride.tools.mzxml_parser.MzXMLFile;
import uk.ac.ebi.pride.tools.mzxml_parser.MzXMLParsingException;
import uk.ac.ebi.pride.tools.pkl_parser.PklFile;
import uk.ac.ebi.pride.tools.pride_wrapper.PRIDEXmlWrapper;
import uk.ac.ebi.pride.utilities.util.Triple;
import uk.ac.ebi.pride.utilities.util.Tuple;

import java.io.File;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public class JmzReaderSpectrumService {
    public static final Pattern patternScanInTitle = Pattern.compile("^.*scan=(\\d+).*$");

    /**
     * Map of all readers containing the spectra
     */
    Map<String, JMzReader> readers;

    /**
     * Based on the file type of the peak file, get the correct reader to parse the peak file
     * @param spectrumFileList spectrum File List
     * @throws JMzReaderException JMzReader Exception
     * @throws MzXMLParsingException MzXML Parsing Exception
     */
    private JmzReaderSpectrumService(List<Triple<String, SpectraData, Utility.FileType>> spectrumFileList) throws JMzReaderException, MzXMLParsingException {
        this.readers = new HashMap<>();
        for (Triple<String, SpectraData, Utility.FileType> entry : spectrumFileList) {
            String key = Paths.get(entry.getFirst()).toString();
            Utility.FileType value = entry.getThird();

            if (value == Utility.FileType.MGF) {
                this.readers.put(key, new MgfFile(new File(key), true));
            }
            if (value == Utility.FileType.PRIDE) {
                this.readers.put(key, new PRIDEXmlWrapper(new File(key)));
            }
            if( value == Utility.FileType.MZML){
                this.readers.put(key, new MzMlWrapper(new File(key)));
            }
            if( value == Utility.FileType.PKL){
                this.readers.put(key, new PklFile(new File(key)));
            }
            if( value == Utility.FileType.MZXML){
                this.readers.put(key, new MzXMLFile(new File(key)));
            }
            if( value == Utility.FileType.MS2){
                this.readers.put(key, new Ms2File(new File(key)));
            }
        }
    }

    /**
     * Return an instance that allow to read the spectra from the original file.
     *
     * @param spectrumFileList spectrum File List
     * @return JmzReaderSpectrumService JmzReader Spectrum Service
     * @throws JMzReaderException JMzReader Exception
     * @throws MzXMLParsingException MzXMLParsingException Exception
     */
    public static JmzReaderSpectrumService getInstance(List<Triple<String, SpectraData, Utility.FileType>> spectrumFileList) throws JMzReaderException, MzXMLParsingException {
        return new JmzReaderSpectrumService(spectrumFileList);
    }

    /**
     * Get the Spectrum by the spectrum Id
     * @param filePath absolute file path
     * @param id spectrum Id
     * @return Spectrum
     * @throws JMzReaderException JMzReader Exception
     */
    public Spectrum getSpectrumById(String filePath, String id) throws JMzReaderException {
        JMzReader reader = readers.get(filePath);

        try{
            // Remove the scan prefix if the id starts like scan=ScanNum
            if(id.startsWith("scan="))
                id = id.replace("scan=", "");

            Spectrum spec = null;
            if(reader.getSpectraIds().contains(id)){
                spec = reader.getSpectrumById(id);
            }

            /***
             * In some cases the id is written wronly and the scan is annotated as controllerType=0 controllerNumber=1 scan=1 while in the
             * mzidentml is annotated as scan=1
             */
            String finalId = id;
            if (spec == null){

                List<Tuple<String, String>> spectra = reader.getSpectraIds()
                        .stream().filter(x -> x.contains(finalId))
                        .map(x -> new Tuple<>(x, x)).collect(Collectors.toList());
                if(spectra.size() == 1)
                    spec =  reader.getSpectrumById(spectra.get(0).getValue());
                else{
                    spectra = reader.getSpectraIds().stream().map(x-> {
                        String[] scans = x.split(" ");
                        for(String idScan: scans){
                            if(idScan.contains("scan")){
                                return new Tuple<>(idScan.trim(),x);
                            }
                        }
                        return new Tuple<>(x,x);
                    }).collect(Collectors.toList()).stream().filter(x -> x.getKey().equalsIgnoreCase("scan=" + finalId)).collect(Collectors.toList());
                    if(spectra.size() == 1)
                        spec =  reader.getSpectrumById(spectra.get(0).getValue());
                }
            }
            if(spec != null && spec.getMsLevel() == 1)
                spec = null;
            return spec;
        }catch (NumberFormatException e){
            throw new JMzReaderException("Error parsing the following Accession -- " + id);
        }
    }

    /**
     * Get the Spectrum by the spectrum index
     * @param filePath absolute file path
     * @param id spectrum index
     * @return Spectrum
     * @throws JMzReaderException JMzReader Exception
     */
    public Spectrum getSpectrumByIndex(String filePath, int id) throws JMzReaderException {
        JMzReader reader = readers.get(filePath);
        try{
            return reader.getSpectrumByIndex(id);
        }catch (NumberFormatException e){
            throw new JMzReaderException("Error parsing the following Accession -- " + id);
        }
    }
}