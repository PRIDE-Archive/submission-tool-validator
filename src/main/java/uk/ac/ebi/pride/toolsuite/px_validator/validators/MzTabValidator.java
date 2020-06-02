package uk.ac.ebi.pride.toolsuite.px_validator.validators;


import de.mpc.pia.intermediate.compiler.PIASimpleCompiler;
import de.mpc.pia.intermediate.compiler.parser.InputFileParserFactory;
import org.apache.commons.cli.CommandLine;
import uk.ac.ebi.pride.data.validation.ValidationMessage;
import uk.ac.ebi.pride.jmztab.utils.MZTabFileParser;
import uk.ac.ebi.pride.jmztab.utils.errors.MZTabError;
import uk.ac.ebi.pride.jmztab.utils.errors.MZTabErrorType;
import uk.ac.ebi.pride.toolsuite.px_validator.utils.IReport;
import uk.ac.ebi.pride.toolsuite.px_validator.utils.ResultReport;
import uk.ac.ebi.pride.toolsuite.px_validator.utils.Utility;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class MzTabValidator implements Validator{

    private File file;

    public static Validator getInstance(CommandLine cmd) throws Exception {
        return new MzTabValidator(cmd);
    }

    private MzTabValidator(CommandLine cmd) throws Exception{

        if(cmd.hasOption(Utility.ARG_MZTAB)){
            file = new File(cmd.getOptionValue(Utility.ARG_MZTAB));
            if (!file.exists()){
                throw new IOException("The provided file name can't be found -- "
                        + cmd.getOptionValue(Utility.ARG_MZTAB));
            }
        }else{
            throw new IOException("In order to validate a mztab file the argument -mztab should be provided");
        }
    }

    @Override
    public IReport validate() {
        ResultReport report = new ResultReport();
        try {
            MZTabFileParser mzTab = new MZTabFileParser(file, new FileOutputStream(file.getAbsolutePath() + "-mztab-errors.out"));

            PIASimpleCompiler piaCompiler = new PIASimpleCompiler();
            piaCompiler.getDataFromFile(file.getName(), file.getAbsolutePath(), null, InputFileParserFactory.InputFileTypes.MZTAB_INPUT.getFileTypeShort());
            piaCompiler.buildClusterList();
            piaCompiler.buildIntermediateStructure();

            int numProteins = piaCompiler.getNrAccessions();
            int numPeptides = piaCompiler.getNrPeptides();
            int numPSMs = piaCompiler.getNrPeptideSpectrumMatches();

            report.setNumberOfPeptides(numPeptides);
            report.setNumberOfProteins(numProteins);
            report.setNumberOfPSMs(numPSMs);

            for(MZTabError message: mzTab.getErrorList().getErrorList()){
                ValidationMessage.Type errType;

                if(message.getType().getLevel() == MZTabErrorType.Level.Error)
                    errType = ValidationMessage.Type.ERROR;
                else if(message.getType().getLevel() == MZTabErrorType.Level.Warn)
                    errType = ValidationMessage.Type.WARNING;
                else
                    errType = ValidationMessage.Type.INFO;
                report.addException(new IOException(message.getMessage()), errType);
            }

        } catch (IOException e) {
            report.addException(e, ValidationMessage.Type.ERROR);

        }
        return report;
    }
}
